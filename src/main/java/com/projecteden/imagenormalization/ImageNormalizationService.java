package com.projecteden.imagenormalization;

import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

public interface ImageNormalizationService {

	NormalizedImage normalize(UploadedImagePayload input);
}
