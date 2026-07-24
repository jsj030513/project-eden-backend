package com.projecteden.memorytaxonomy.legacy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.memorytaxonomy.domain.MemoryClassification;
import com.projecteden.memorytaxonomy.domain.MemoryTag;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.village.domain.VillageCategory;

@Service
public class LegacyMemoryClassificationWriter {

	public static final String PROVIDER = "LEGACY_MOCK";
	public static final String MODEL_VERSION = "mock-filename-v1";
	public static final String TAXONOMY_VERSION = MemoryClassification.DEFAULT_TAXONOMY_VERSION;

	private static final Logger log =
			LoggerFactory.getLogger(LegacyMemoryClassificationWriter.class);

	private final PhotoRepository photos;
	private final RecognitionRepository recognitions;
	private final MemoryClassificationRepository classifications;
	private final MemoryTaxonomyCategoryRepository taxonomyCategories;
	private final MemoryTagRepository tags;
	private final MemoryClassificationTagRepository classificationTags;
	private final LegacyVillageCategoryMapper categoryMapper;
	private final LegacyMemoryTagMapper tagMapper;

	public LegacyMemoryClassificationWriter(
			PhotoRepository photos,
			RecognitionRepository recognitions,
			MemoryClassificationRepository classifications,
			MemoryTaxonomyCategoryRepository taxonomyCategories,
			MemoryTagRepository tags,
			MemoryClassificationTagRepository classificationTags,
			LegacyVillageCategoryMapper categoryMapper,
			LegacyMemoryTagMapper tagMapper) {
		this.photos = photos;
		this.recognitions = recognitions;
		this.classifications = classifications;
		this.taxonomyCategories = taxonomyCategories;
		this.tags = tags;
		this.classificationTags = classificationTags;
		this.categoryMapper = categoryMapper;
		this.tagMapper = tagMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<MemoryClassification> writeFromLegacyRecognition(
			Long photoId,
			Long recognitionId) {
		Optional<MemoryClassification> existing =
				classifications.findFirstByRecognitionIdAndProviderAndModelVersionAndTaxonomyVersion(
						recognitionId,
						PROVIDER,
						MODEL_VERSION,
						TAXONOMY_VERSION);
		if (existing.isPresent()) {
			return existing;
		}

		Photo photo = photos.findById(photoId)
				.orElseThrow(() -> new IllegalArgumentException("사진을 찾을 수 없습니다."));
		Recognition recognition = recognitions.findById(recognitionId)
				.orElseThrow(() -> new IllegalArgumentException("인식 결과를 찾을 수 없습니다."));
		if (!recognition.getPhoto().getId().equals(photo.getId())) {
			throw new IllegalArgumentException("사진과 인식 결과가 일치하지 않습니다.");
		}

		boolean fallback = categoryMapper.shouldFallback(
				recognition.isRecognized(),
				recognition.getRecognizedObject());
		MemoryTaxonomyCategory primaryCategory = null;
		Optional<String> primaryCode =
				categoryMapper.toTaxonomyCategoryCode(recognition.getRecognizedObject());
		if (!fallback && primaryCode.isPresent()) {
			primaryCategory = taxonomyCategories.findByCode(primaryCode.get())
					.orElseGet(() -> {
						log.warn(
								"Legacy taxonomy category code is missing. photoId={}, recognitionId={}, categoryCode={}",
								photoId,
								recognitionId,
								primaryCode.get());
						return null;
					});
			if (primaryCategory == null) {
				fallback = true;
			}
		}

		MemoryClassification classification = classifications.save(MemoryClassification.create(
				photo,
				recognition,
				primaryCategory,
				observation(recognition, fallback),
				null,
				confidence(recognition),
				PROVIDER,
				MODEL_VERSION,
				TAXONOMY_VERSION,
				fallback));

		for (String tagCode : tagMapper.toTagCodes(recognition.getRecognizedObject())) {
			Optional<MemoryTag> tag = tags.findByCode(tagCode);
			if (tag.isPresent()) {
				classificationTags.save(classification.addTag(tag.get(), null));
			} else {
				log.warn(
						"Legacy memory tag code is missing. photoId={}, recognitionId={}, tagCode={}",
						photoId,
						recognitionId,
						tagCode);
			}
		}

		log.debug(
				"Legacy classification dual-write completed. photoId={}, recognitionId={}, classificationId={}",
				photoId,
				recognitionId,
				classification.getId());
		return Optional.of(classification);
	}

	private Map<String, Object> observation(Recognition recognition, boolean fallback) {
		Map<String, Object> observation = new LinkedHashMap<>();
		VillageCategory category = recognition.getRecognizedObject() == null
				? null
				: recognition.getRecognizedObject().getCategory();
		observation.put("source", "legacy-recognition");
		observation.put("recognizedObject", recognition.getRecognizedObject().name());
		observation.put("legacyCategory", category == null ? null : category.name());
		observation.put("recognized", recognition.isRecognized());
		observation.put("fallback", fallback);
		observation.put("confidence", confidence(recognition));
		return observation;
	}

	private BigDecimal confidence(Recognition recognition) {
		return BigDecimal.valueOf(recognition.getConfidence(), 2);
	}
}
