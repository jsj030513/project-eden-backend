package com.projecteden.vision.eden;

import com.projecteden.vision.detection.DetectionObject;

/** A deterministic interpretation of one raw COCO detection; the source detection remains intact. */
public record EdenMappedObject(DetectionObject source, EdenObjectCode code, EdenObjectFamily family, String mappingVersion) { }
