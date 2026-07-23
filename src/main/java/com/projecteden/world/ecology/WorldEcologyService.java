package com.projecteden.world.ecology;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.world.repository.WorldRepository;

@Service
public class WorldEcologyService {
    public static final int MIN_X=0, MAX_X=23, MIN_Y=0, MAX_Y=15;
    private static final int TEMPLATE_VERSION = 2;
    private final WorldChangeRepository changes; private final WorldPlacedObjectRepository objects; private final CharacterRepository characters; private final WorldTerrainTileRepository terrain; private final WorldPlayerPositionRepository positions; private final WorldRepository worlds;
    public WorldEcologyService(WorldChangeRepository changes, WorldPlacedObjectRepository objects, CharacterRepository characters, WorldTerrainTileRepository terrain, WorldPlayerPositionRepository positions, WorldRepository worlds) { this.changes=changes; this.objects=objects; this.characters=characters; this.terrain=terrain; this.positions=positions; this.worlds=worlds; }
    @Transactional public WorldChangeResult createFor(Recognition recognition) {
        return changes.findByRecognitionId(recognition.getId()).map(this::result).orElseGet(() -> create(recognition));
    }
    @Transactional(readOnly=true) public WorldChangeResult findFor(Long recognitionId) { return changes.findByRecognitionId(recognitionId).map(this::result).orElse(null); }
    @Transactional public WorldStateResponse stateForUser(Long userId) {
        var character=characters.findByUserId(userId).orElseThrow(()->new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
        bootstrap(character);
        var position=position(character);
        Long characterId=character.getId();
        var worldChanges=changes.findByCharacterIdOrderByIdAsc(characterId);
        Set<Long> replacedObjectIds = worldChanges.stream()
                .map(WorldChange::getTargetObject)
                .filter(java.util.Objects::nonNull)
                .map(WorldPlacedObject::getId)
                .collect(Collectors.toSet());
        var placed = worldChanges.stream().flatMap(c->objects.findByWorldChangeId(c.getId()).stream()
                .filter(object -> !replacedObjectIds.contains(object.getId()))
                .map(o->new PlacedObjectResponse(o.getId(),o.getAssetType(),c.getWorldCategory(),o.getPositionX(),o.getPositionY(),o.getTerrain(),o.getHabitat(),c.getId(),o.getPositionY(),0))).toList();
        Map<String, List<PlacedObjectResponse>> npcByTile = placed.stream()
                .filter(object -> isTemplateNpc(object.assetType()))
                .collect(Collectors.groupingBy(object -> tileKey(object.x(), object.y())));
        Map<String, List<PlacedObjectResponse>> contextualByTile = placed.stream()
                .filter(object -> contextualInteraction(object.assetType()) != null)
                .collect(Collectors.groupingBy(object -> tileKey(object.x(), object.y())));
        var npcPositions = placed.stream().filter(object -> isTemplateNpc(object.assetType())).map(object -> new NpcPositionResponse(
                object.id(), object.assetType(), npcName(object.assetType()), object.x() / 48, object.y() / 48, TileInteractionType.TALK)).toList();
        var interactions=java.util.stream.Stream.of(new int[]{0,-1},new int[]{0,1},new int[]{-1,0},new int[]{1,0})
                .map(d->terrain.findByCharacterIdAndXAndY(characterId,position.getX()+d[0],position.getY()+d[1]).map(t->{
                    String key = tileKey(t.getX()*48,t.getY()*48);
                    var npc = firstDeterministic(npcByTile.get(key));
                    if (npc != null) {
                        return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.TALK,true,null,
                                npc.id(),npc.assetType(),npcName(npc.assetType()));
                    }
                    var contextual = firstDeterministic(contextualByTile.get(key));
                    if (contextual != null) {
                        ContextualInteraction metadata = contextualInteraction(contextual.assetType());
                        return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.INTERACT,true,null,
                                contextual.id(),contextual.assetType(),metadata.displayName(),metadata.category(),metadata.actionLabel());
                    }
                    return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.INSPECT,true,null);
                })).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt((TileInteractionResponse interaction) -> interactionPriority(interaction.type()))
                        .thenComparingInt(TileInteractionResponse::y)
                        .thenComparingInt(TileInteractionResponse::x)
                        .thenComparing(interaction -> interaction.targetAssetType() == null ? "" : interaction.targetAssetType().name())
                        .thenComparing(interaction -> interaction.targetId() == null ? Long.MAX_VALUE : interaction.targetId()))
                .toList();
        return new WorldStateResponse(worldChanges.stream().map(this::result).toList(),terrain.findByCharacterIdOrderByYAscXAsc(characterId).stream().map(t->new TerrainTileResponse(t.getX(),t.getY(),t.getTerrainType(),t.isWalkable())).toList(),placed,new MapBoundsResponse(MIN_X,MAX_X,MIN_Y,MAX_Y),new PlayerPositionResponse(position.getX(),position.getY()),interactions,npcPositions,List.of(),List.of(),"LIVING_VILLAGE",character.getName()+"의 마을");
    }
    @Transactional public MoveResponse move(Long userId,MoveRequest request){ var character=characters.findByUserId(userId).orElseThrow(()->new ResourceNotFoundException("캐릭터를 찾을 수 없습니다.")); bootstrap(character); var current=position(character); if(Math.abs(request.targetX()-current.getX())+Math.abs(request.targetY()-current.getY())>1)return new MoveResponse(false,current.getX(),current.getY(),null,"MOVE_TOO_FAR"); if(request.targetX()<MIN_X||request.targetX()>MAX_X||request.targetY()<MIN_Y||request.targetY()>MAX_Y)return new MoveResponse(false,current.getX(),current.getY(),null,"OUT_OF_BOUNDS"); var tile=terrain.findByCharacterIdAndXAndY(character.getId(),request.targetX(),request.targetY()).orElseThrow(); if(!tile.isWalkable())return new MoveResponse(false,current.getX(),current.getY(),tile.getTerrainType(),"TERRAIN_BLOCKED"); current.moveTo(request.targetX(),request.targetY()); return new MoveResponse(true,current.getX(),current.getY(),tile.getTerrainType(),"OK"); }
    WorldPlayerPosition position(com.projecteden.character.domain.Character c){return positions.findByCharacterId(c.getId()).orElseGet(()->positions.save(WorldPlayerPosition.create(c,11,8)));}
    private void bootstrap(com.projecteden.character.domain.Character character){ if(!terrain.existsByCharacterId(character.getId())) for(int y=MIN_Y;y<=MAX_Y;y++)for(int x=MIN_X;x<=MAX_X;x++){ TerrainType type=(x==11||y==7)?TerrainType.ROAD:TerrainType.GRASS; if(x<2||x>21||y<2||y>13)type=TerrainType.FOREST; if(x>=17&&y>=11)type=TerrainType.WATER; if(x>=13&&x<=15&&y>=3&&y<=5)type=TerrainType.BUILDING; terrain.save(WorldTerrainTile.create(character,x,y,type)); } seedTemplate(character); }
    private void seedTemplate(com.projecteden.character.domain.Character character) {
        var world = worlds.findByCharacterId(character.getId()).orElse(null);
        if (world != null && world.getVillageTemplateVersion() >= TEMPLATE_VERSION) return;
        soil(character, 2, 9, 4, 2); // empty plot
        soil(character, 2, 12, 4, 2); // carrot rows
        soil(character, 3, 4, 4, 2); // flower rows
        soil(character, 7, 10, 4, 2); // mixed vegetables
        template(character,"TEMPLATE_PLAZA",WorldCategory.MEMORY,WorldAssetType.PLAZA,11,7);
        template(character,"TEMPLATE_FARM_EMPTY",WorldCategory.NATURE,WorldAssetType.FARM_PLOT_EMPTY,3,9);
        cropRow(character,"TEMPLATE_CARROT",WorldCategory.FOOD,WorldAssetType.FARM_CARROT,2,12,4,2);
        cropRow(character,"TEMPLATE_FLOWER",WorldCategory.NATURE,WorldAssetType.FARM_FLOWER,3,4,4,2);
        cropRow(character,"TEMPLATE_TOMATO",WorldCategory.FOOD,WorldAssetType.FARM_TOMATO,7,10,2,2);
        cropRow(character,"TEMPLATE_CABBAGE",WorldCategory.FOOD,WorldAssetType.FARM_CABBAGE,9,10,2,2);
        template(character,"TEMPLATE_HOUSE",WorldCategory.MEMORY,WorldAssetType.COMMUNITY_HOUSE,7,4);
        template(character,"TEMPLATE_GUIDE",WorldCategory.MEMORY,WorldAssetType.DEFAULT_NPC_GUIDE,10,6);
        template(character,"TEMPLATE_GARDENER",WorldCategory.NATURE,WorldAssetType.DEFAULT_NPC_GARDENER,5,8);
        template(character,"TEMPLATE_KEEPER",WorldCategory.MEMORY,WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER,12,9);
        template(character,"TEMPLATE_CARETAKER",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER,16,8);
        template(character,"TEMPLATE_DOG",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_DOG,17,9);
        template(character,"TEMPLATE_CAT",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_CAT,18,9);
        template(character,"TEMPLATE_BIRD_A",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_BIRD,19,8);
        template(character,"TEMPLATE_BIRD_B",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_BIRD,20,8);
        if (world != null) world.applyVillageTemplateVersion(TEMPLATE_VERSION);
    }
    private void template(com.projecteden.character.domain.Character character,String key,WorldCategory category,WorldAssetType asset,int tileX,int tileY) { if(changes.findByCharacterIdAndMessageKey(character.getId(),key).isPresent())return; var change=changes.save(WorldChange.template(character,category,asset,key,"기본 마을 풍경",tileX*48,tileY*48)); objects.save(WorldPlacedObject.create(change,asset,TerrainType.GRASS,HabitatType.DECORATION_ONLY,tileX*48,tileY*48)); }
    private void cropRow(com.projecteden.character.domain.Character c,String prefix,WorldCategory category,WorldAssetType asset,int x,int y,int width,int height){ for(int row=0;row<height;row++)for(int column=0;column<width;column++)template(c,prefix+"_"+row+"_"+column,category,asset,x+column,y+row); }
    private void soil(com.projecteden.character.domain.Character c,int x,int y,int width,int height){ for(int row=0;row<height;row++)for(int column=0;column<width;column++)terrain.findByCharacterIdAndXAndY(c.getId(),x+column,y+row).ifPresent(tile -> { if(tile.getTerrainType()==TerrainType.GRASS)tile.changeTerrain(TerrainType.SOIL); }); }
    private static String tileKey(int x, int y) { return x + ":" + y; }
    private static PlacedObjectResponse firstDeterministic(List<PlacedObjectResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.stream()
                .sorted(Comparator.comparing((PlacedObjectResponse object) -> object.assetType().name())
                        .thenComparing(PlacedObjectResponse::id))
                .findFirst()
                .orElse(null);
    }
    private static int interactionPriority(TileInteractionType type) {
        return switch (type) {
            case TALK -> 0;
            case INTERACT -> 1;
            case INSPECT -> 2;
        };
    }
    private static ContextualInteraction contextualInteraction(WorldAssetType type) {
        return switch (type) {
            case FARM_PLOT_EMPTY -> new ContextualInteraction(TileInteractionCategory.FARM, "비어 있는 밭", "살펴보기");
            case FARM_CARROT -> new ContextualInteraction(TileInteractionCategory.CROP, "당근밭", "작물 살펴보기");
            case FARM_FLOWER -> new ContextualInteraction(TileInteractionCategory.CROP, "꽃밭", "작물 살펴보기");
            case FARM_VEGETABLE -> new ContextualInteraction(TileInteractionCategory.CROP, "채소밭", "작물 살펴보기");
            case FARM_TOMATO -> new ContextualInteraction(TileInteractionCategory.CROP, "토마토밭", "작물 살펴보기");
            case FARM_CABBAGE -> new ContextualInteraction(TileInteractionCategory.CROP, "양배추밭", "작물 살펴보기");
            case DEFAULT_DOG -> new ContextualInteraction(TileInteractionCategory.ANIMAL, "강아지", "다가가기");
            case DEFAULT_CAT -> new ContextualInteraction(TileInteractionCategory.ANIMAL, "고양이", "다가가기");
            case DEFAULT_BIRD -> new ContextualInteraction(TileInteractionCategory.ANIMAL, "새", "다가가기");
            case COMMUNITY_HOUSE -> new ContextualInteraction(TileInteractionCategory.COMMUNITY, "마을 회관", "둘러보기");
            default -> null;
        };
    }
    private static boolean isTemplateNpc(WorldAssetType type) { return type == WorldAssetType.DEFAULT_NPC_GUIDE || type == WorldAssetType.DEFAULT_NPC_GARDENER || type == WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER || type == WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER; }
    private static String npcName(WorldAssetType type) { return switch (type) { case DEFAULT_NPC_GUIDE -> "마을 안내자"; case DEFAULT_NPC_GARDENER -> "정원 관리인"; case DEFAULT_NPC_MEMORY_KEEPER -> "기억 보관인"; case DEFAULT_NPC_ANIMAL_CARETAKER -> "동물 돌봄이"; default -> "마을 주민"; }; }
    private record ContextualInteraction(TileInteractionCategory category, String displayName, String actionLabel) { }
    private WorldChangeResult create(Recognition recognition) {
        Mapping mapping = mappingFor(recognition.getRecognizedObject()); Random random = new Random(31L * recognition.getId() + recognition.getPhoto().getId());
        int x = 140 + random.nextInt(940), y = mapping.terrain == TerrainType.WATER ? 640 + random.nextInt(110) : 300 + random.nextInt(360);
        WorldChange change = changes.save(WorldChange.create(recognition.getPhoto().getCharacter(), recognition, mapping.category, mapping.asset, mapping.key, mapping.message, x, y));
        int count = mapping.asset == WorldAssetType.FLOWER_CLUSTER ? 5 : mapping.asset == WorldAssetType.TREE_GROVE ? 4 : 1;
        List<Long> ids = java.util.stream.IntStream.range(0,count).mapToObj(i -> objects.save(WorldPlacedObject.create(change, mapping.asset, mapping.terrain, mapping.habitat, x + (i * 12), y + (i % 2) * 10)).getId()).toList();
        return new WorldChangeResult(change.getId(), mapping.category, mapping.asset, mapping.key, mapping.message, ids, true, x, y);
    }
    WorldChangeResult createTargeted(Recognition recognition, WorldPlacedObject target, WorldAssetType cropAsset) {
        WorldCategory category = cropAsset == WorldAssetType.FARM_FLOWER ? WorldCategory.NATURE : WorldCategory.FOOD;
        String message = cropAsset == WorldAssetType.FARM_FLOWER
                ? "이 기억이 빈 밭에 꽃으로 피어났습니다."
                : "이 기억이 빈 밭에 새로운 작물로 자라났습니다.";
        WorldChange change = changes.saveAndFlush(WorldChange.targeted(
                recognition.getPhoto().getCharacter(), recognition, target, category, cropAsset,
                "TARGETED_PLANTING", message, target.getPositionX(), target.getPositionY()));
        objects.saveAndFlush(WorldPlacedObject.create(
                change, cropAsset, TerrainType.SOIL, HabitatType.DECORATION_ONLY,
                target.getPositionX(), target.getPositionY()));
        return result(change);
    }
    WorldChangeResult result(WorldChange change) { return new WorldChangeResult(change.getId(),change.getWorldCategory(),change.getAssetType(),change.getMessageKey(),change.getDisplayMessage(),objects.findByWorldChangeId(change.getId()).stream().map(WorldPlacedObject::getId).toList(),true,change.getFocusX(),change.getFocusY()); }
    private Mapping mappingFor(RecognizedObject object) {
        if (object == null || object == RecognizedObject.UNKNOWN) return new Mapping(WorldCategory.UNKNOWN,WorldAssetType.MEMORY_SPARK,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"MEMORY","특별한 기억이 마을 어딘가에 작은 변화를 남겼습니다.");
        return switch(object) { case FLOWER, PLANT -> new Mapping(WorldCategory.NATURE,WorldAssetType.FLOWER_CLUSTER,TerrainType.FLOWER_FIELD,HabitatType.DECORATION_ONLY,"FLOWER","이 기억은 마을의 새로운 풍경이 되었습니다."); case TREE -> new Mapping(WorldCategory.NATURE,WorldAssetType.TREE_GROVE,TerrainType.FOREST,HabitatType.DECORATION_ONLY,"TREE","오래 머물 그늘이 마을에 생겼습니다."); case CAT,DOG,ANIMAL -> new Mapping(WorldCategory.ANIMAL,WorldAssetType.VISITOR,TerrainType.GRASS,HabitatType.LAND,"LAND_ANIMAL","새로운 손님이 마을을 찾아왔습니다."); case WATER,RIVER,SEA,POND -> new Mapping(WorldCategory.WATER,WorldAssetType.POND,TerrainType.WATER,HabitatType.DECORATION_ONLY,"WATER","물가에 새로운 친구가 찾아왔습니다."); case FOOD,BREAD,FRUIT,VEGETABLE,TOMATO,CARROT,POTATO,WHEAT,COFFEE -> new Mapping(WorldCategory.FOOD,WorldAssetType.BAKERY_DETAIL,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"FOOD","마을에 맛있는 향기가 퍼졌습니다."); case SKY,LANDSCAPE -> new Mapping(WorldCategory.SKY,WorldAssetType.ATMOSPHERE,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"SKY","오늘의 하늘이 마을에 스며들었습니다."); default -> new Mapping(WorldCategory.MEMORY,WorldAssetType.MEMORY_SPARK,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"MEMORY","특별한 기억이 마을 어딘가에 작은 변화를 남겼습니다."); };
    }
    private record Mapping(WorldCategory category, WorldAssetType asset, TerrainType terrain, HabitatType habitat, String key, String message) { }
}
