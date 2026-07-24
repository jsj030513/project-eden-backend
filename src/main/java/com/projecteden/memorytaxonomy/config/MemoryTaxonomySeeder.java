package com.projecteden.memorytaxonomy.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.memorytaxonomy.domain.MemoryTag;
import com.projecteden.memorytaxonomy.domain.MemoryTagType;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategoryType;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;

@Component
public class MemoryTaxonomySeeder {

	private final MemoryTaxonomyCategoryRepository categories;
	private final MemoryTagRepository tags;

	public MemoryTaxonomySeeder(
			MemoryTaxonomyCategoryRepository categories,
			MemoryTagRepository tags) {
		this.categories = categories;
		this.tags = tags;
	}

	@Transactional
	public void seed() {
		seedCategories();
		seedTags();
	}

	private void seedCategories() {
		category("NATURE", "자연", MemoryTaxonomyCategoryType.DOMAIN, 10);
		category("ANIMAL", "동물", MemoryTaxonomyCategoryType.DOMAIN, 20);
		category("FOOD", "음식", MemoryTaxonomyCategoryType.DOMAIN, 30);
		category("WATER", "물가", MemoryTaxonomyCategoryType.PLACE, 40);
		category("WALK", "산책", MemoryTaxonomyCategoryType.ACTIVITY, 50);
		category("STUDY", "공부", MemoryTaxonomyCategoryType.ACTIVITY, 60);
		category("WORK", "업무", MemoryTaxonomyCategoryType.ACTIVITY, 70);
		category("PEOPLE", "사람", MemoryTaxonomyCategoryType.RELATIONSHIP, 80);
		category("FAMILY", "가족", MemoryTaxonomyCategoryType.RELATIONSHIP, 90);
		category("FRIENDS", "친구", MemoryTaxonomyCategoryType.RELATIONSHIP, 100);
		category("DAILY_LIFE", "일상", MemoryTaxonomyCategoryType.DOMAIN, 110);
		category("EXERCISE", "운동", MemoryTaxonomyCategoryType.ACTIVITY, 120);
		category("TRAVEL", "여행", MemoryTaxonomyCategoryType.ACTIVITY, 130);
		category("CULTURE", "문화생활", MemoryTaxonomyCategoryType.ACTIVITY, 140);
		category("EXHIBITION", "전시", MemoryTaxonomyCategoryType.ACTIVITY, 150);
		category("MUSIC", "음악", MemoryTaxonomyCategoryType.DOMAIN, 160);
		category("MOVIE", "영화", MemoryTaxonomyCategoryType.DOMAIN, 170);
		category("SHOPPING", "쇼핑", MemoryTaxonomyCategoryType.ACTIVITY, 180);
		category("REST", "휴식", MemoryTaxonomyCategoryType.ACTIVITY, 190);
		category("EMOTION", "감정", MemoryTaxonomyCategoryType.MOOD, 200);
		category("PLACE", "장소", MemoryTaxonomyCategoryType.PLACE, 210);
	}

	private void seedTags() {
		tag("CAT", "고양이", MemoryTagType.SUBJECT);
		tag("DOG", "강아지", MemoryTagType.SUBJECT);
		tag("PERSON", "사람", MemoryTagType.SUBJECT);
		tag("FLOWER", "꽃", MemoryTagType.SUBJECT);
		tag("TREE", "나무", MemoryTagType.SUBJECT);
		tag("FOOD", "음식", MemoryTagType.SUBJECT);
		tag("BOOK", "책", MemoryTagType.SUBJECT);
		tag("COMPUTER", "컴퓨터", MemoryTagType.SUBJECT);

		tag("INDOOR", "실내", MemoryTagType.SCENE);
		tag("OUTDOOR", "야외", MemoryTagType.SCENE);
		tag("PARK", "공원", MemoryTagType.SCENE);
		tag("HOME", "집", MemoryTagType.SCENE);
		tag("CAFE", "카페", MemoryTagType.SCENE);
		tag("OFFICE", "사무실", MemoryTagType.SCENE);
		tag("SCHOOL", "학교", MemoryTagType.SCENE);

		tag("WALKING", "걷기", MemoryTagType.ACTIVITY);
		tag("STUDYING", "공부하기", MemoryTagType.ACTIVITY);
		tag("WORKING", "일하기", MemoryTagType.ACTIVITY);
		tag("EATING", "식사하기", MemoryTagType.ACTIVITY);
		tag("RESTING", "쉬기", MemoryTagType.ACTIVITY);
		tag("TRAVELING", "여행하기", MemoryTagType.ACTIVITY);

		tag("WARM", "따뜻함", MemoryTagType.MOOD);
		tag("CALM", "평온함", MemoryTagType.MOOD);
		tag("JOYFUL", "즐거움", MemoryTagType.MOOD);
		tag("QUIET", "고요함", MemoryTagType.MOOD);
		tag("ENERGETIC", "활기참", MemoryTagType.MOOD);

		tag("ALONE", "혼자", MemoryTagType.RELATIONSHIP);
		tag("FRIENDS", "친구와 함께", MemoryTagType.RELATIONSHIP);
		tag("FAMILY", "가족과 함께", MemoryTagType.RELATIONSHIP);

		tag("BENCH", "벤치", MemoryTagType.OBJECT);
		tag("BRIDGE", "다리", MemoryTagType.OBJECT);
		tag("TABLE", "테이블", MemoryTagType.OBJECT);
		tag("ROAD", "길", MemoryTagType.OBJECT);
		tag("WATER", "물", MemoryTagType.OBJECT);
	}

	private void category(
			String code,
			String displayName,
			MemoryTaxonomyCategoryType categoryType,
			int sortOrder) {
		if (!categories.existsByCode(code)) {
			categories.save(MemoryTaxonomyCategory.create(code, displayName, categoryType, sortOrder));
		}
	}

	private void tag(String code, String displayName, MemoryTagType tagType) {
		if (!tags.existsByCode(code)) {
			tags.save(MemoryTag.create(code, displayName, tagType));
		}
	}
}
