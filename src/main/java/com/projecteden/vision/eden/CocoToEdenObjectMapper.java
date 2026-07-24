package com.projecteden.vision.eden;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.projecteden.vision.detection.DetectionObject;
import com.projecteden.vision.detection.DetectionResult;

@Component
public class CocoToEdenObjectMapper {
	private static final Map<String, EdenObjectCode> CODES = Map.ofEntries(
			Map.entry("PERSON", EdenObjectCode.PERSON),
			Map.entry("BICYCLE", EdenObjectCode.BICYCLE), Map.entry("CAR", EdenObjectCode.CAR), Map.entry("MOTORCYCLE", EdenObjectCode.MOTORCYCLE), Map.entry("AIRPLANE", EdenObjectCode.VEHICLE), Map.entry("BUS", EdenObjectCode.BUS), Map.entry("TRAIN", EdenObjectCode.TRAIN), Map.entry("TRUCK", EdenObjectCode.VEHICLE), Map.entry("BOAT", EdenObjectCode.VEHICLE),
			Map.entry("TRAFFIC LIGHT", EdenObjectCode.OUTDOOR), Map.entry("FIRE HYDRANT", EdenObjectCode.OUTDOOR), Map.entry("STOP SIGN", EdenObjectCode.OUTDOOR), Map.entry("PARKING METER", EdenObjectCode.OUTDOOR), Map.entry("BENCH", EdenObjectCode.CHAIR),
			Map.entry("BIRD", EdenObjectCode.BIRD), Map.entry("CAT", EdenObjectCode.CAT), Map.entry("DOG", EdenObjectCode.DOG), Map.entry("HORSE", EdenObjectCode.HORSE), Map.entry("SHEEP", EdenObjectCode.SHEEP), Map.entry("COW", EdenObjectCode.COW), Map.entry("ELEPHANT", EdenObjectCode.WILD_ANIMAL), Map.entry("BEAR", EdenObjectCode.WILD_ANIMAL), Map.entry("ZEBRA", EdenObjectCode.WILD_ANIMAL), Map.entry("GIRAFFE", EdenObjectCode.WILD_ANIMAL),
			Map.entry("BACKPACK", EdenObjectCode.BAG), Map.entry("UMBRELLA", EdenObjectCode.OUTDOOR), Map.entry("HANDBAG", EdenObjectCode.BAG), Map.entry("TIE", EdenObjectCode.CLOTHING), Map.entry("SUITCASE", EdenObjectCode.BAG),
			Map.entry("FRISBEE", EdenObjectCode.SPORT), Map.entry("SKIS", EdenObjectCode.SPORT), Map.entry("SNOWBOARD", EdenObjectCode.SPORT), Map.entry("SPORTS BALL", EdenObjectCode.SPORT), Map.entry("KITE", EdenObjectCode.SPORT), Map.entry("BASEBALL BAT", EdenObjectCode.SPORT), Map.entry("BASEBALL GLOVE", EdenObjectCode.SPORT), Map.entry("SKATEBOARD", EdenObjectCode.SPORT), Map.entry("SURFBOARD", EdenObjectCode.SPORT), Map.entry("TENNIS RACKET", EdenObjectCode.SPORT),
			Map.entry("BOTTLE", EdenObjectCode.BOTTLE), Map.entry("WINE GLASS", EdenObjectCode.CUP), Map.entry("CUP", EdenObjectCode.CUP), Map.entry("FORK", EdenObjectCode.FORK), Map.entry("KNIFE", EdenObjectCode.KNIFE), Map.entry("SPOON", EdenObjectCode.SPOON), Map.entry("BOWL", EdenObjectCode.BOWL),
			Map.entry("BANANA", EdenObjectCode.FRUIT), Map.entry("APPLE", EdenObjectCode.FRUIT), Map.entry("SANDWICH", EdenObjectCode.FRUIT), Map.entry("ORANGE", EdenObjectCode.FRUIT), Map.entry("BROCCOLI", EdenObjectCode.VEGETABLE), Map.entry("CARROT", EdenObjectCode.VEGETABLE), Map.entry("HOT DOG", EdenObjectCode.FRUIT), Map.entry("PIZZA", EdenObjectCode.FRUIT), Map.entry("DONUT", EdenObjectCode.FRUIT), Map.entry("CAKE", EdenObjectCode.FRUIT),
			Map.entry("CHAIR", EdenObjectCode.CHAIR), Map.entry("COUCH", EdenObjectCode.COUCH), Map.entry("POTTED PLANT", EdenObjectCode.PLANT), Map.entry("BED", EdenObjectCode.BED), Map.entry("TABLE", EdenObjectCode.TABLE), Map.entry("DINING TABLE", EdenObjectCode.TABLE), Map.entry("TOILET", EdenObjectCode.OTHER_OBJECT),
			Map.entry("TV", EdenObjectCode.MONITOR), Map.entry("LAPTOP", EdenObjectCode.LAPTOP), Map.entry("MOUSE", EdenObjectCode.MOUSE), Map.entry("REMOTE", EdenObjectCode.OTHER_OBJECT), Map.entry("KEYBOARD", EdenObjectCode.KEYBOARD), Map.entry("CELL PHONE", EdenObjectCode.PHONE),
			Map.entry("MICROWAVE", EdenObjectCode.KITCHEN), Map.entry("OVEN", EdenObjectCode.KITCHEN), Map.entry("TOASTER", EdenObjectCode.KITCHEN), Map.entry("SINK", EdenObjectCode.KITCHEN), Map.entry("REFRIGERATOR", EdenObjectCode.KITCHEN),
			Map.entry("BOOK", EdenObjectCode.BOOK),
			Map.entry("CLOCK", EdenObjectCode.OTHER_OBJECT), Map.entry("VASE", EdenObjectCode.OTHER_OBJECT), Map.entry("SCISSORS", EdenObjectCode.OTHER_OBJECT), Map.entry("TEDDY BEAR", EdenObjectCode.TEDDY_BEAR), Map.entry("HAIR DRIER", EdenObjectCode.OTHER_OBJECT), Map.entry("TOOTHBRUSH", EdenObjectCode.OTHER_OBJECT));

	public Optional<EdenMappedObject> map(DetectionObject detection) {
		if (detection == null) return Optional.empty();
		String label = detection.code() == null ? "" : detection.code().trim().toUpperCase(Locale.ROOT);
		if (label.isEmpty()) return Optional.empty();
		EdenObjectCode code = CODES.getOrDefault(label, EdenObjectCode.UNKNOWN_OBJECT);
		return Optional.of(new EdenMappedObject(detection, code, family(code), EdenRuleVersion.OBJECT_MAPPING));
	}

	public List<EdenMappedObject> map(DetectionResult result) {
		if (result == null) return List.of();
		return result.objects().stream().map(this::map).flatMap(Optional::stream).toList();
	}

	private EdenObjectFamily family(EdenObjectCode code) {
		return switch (code) {
			case PERSON -> EdenObjectFamily.HUMAN;
			case CAT, DOG, BIRD, HORSE, SHEEP, COW, WILD_ANIMAL, FARM_ANIMAL -> EdenObjectFamily.ANIMAL;
			case BOOK -> EdenObjectFamily.LEARNING_TOOL;
			case LAPTOP, KEYBOARD, MOUSE, MONITOR, PHONE -> EdenObjectFamily.DIGITAL_DEVICE;
			case CUP, BOTTLE, BOWL, FORK, KNIFE, SPOON, FRUIT -> EdenObjectFamily.FOOD_AND_DRINK;
			case VEGETABLE -> EdenObjectFamily.VEGETABLE;
			case PLANT -> EdenObjectFamily.PLANT;
			case CHAIR, COUCH, BED, TABLE -> EdenObjectFamily.FURNITURE;
			case BICYCLE, MOTORCYCLE, CAR, BUS, TRAIN, VEHICLE -> EdenObjectFamily.TRANSPORT;
			case OUTDOOR -> EdenObjectFamily.OUTDOOR;
			case BAG -> EdenObjectFamily.BAG;
			case SPORT -> EdenObjectFamily.SPORT;
			case KITCHEN -> EdenObjectFamily.KITCHEN;
			case TOY, TEDDY_BEAR -> EdenObjectFamily.TOY;
			case CLOTHING -> EdenObjectFamily.CLOTHING;
			case BUILDING -> EdenObjectFamily.BUILDING;
			default -> EdenObjectFamily.OTHER;
		};
	}
}
