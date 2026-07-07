package com.projecteden.achievement.config;

import org.springframework.boot.ApplicationArguments;import org.springframework.boot.ApplicationRunner;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;
import com.projecteden.achievement.domain.*;import com.projecteden.achievement.repository.AchievementRepository;import com.projecteden.title.domain.Title;import com.projecteden.title.repository.TitleRepository;
@Component public class CollectionAchievementDataInitializer implements ApplicationRunner{
	private final AchievementRepository achievements;private final TitleRepository titles;public CollectionAchievementDataInitializer(AchievementRepository achievements,TitleRepository titles){this.achievements=achievements;this.titles=titles;}
	@Override @Transactional public void run(ApplicationArguments args){title("FIRST_OBSERVER","첫 관찰자","첫 발견을 기록한 사용자");title("SMALL_COLLECTOR","작은 수집가","도감 수집을 시작한 사용자");title("DAILY_OBSERVER","매일 관찰자","꾸준히 자연을 발견한 사용자");title("FOCUSED_OBSERVER","집중 관찰자","한 대상을 반복 관찰한 사용자");achievement("FIRST_DISCOVERY","첫 발견","처음으로 자연 객체를 발견했습니다.",AchievementType.FIRST_DISCOVERY,1,"FIRST_OBSERVER");achievement("COLLECTION_3","작은 수집가","도감에 3종을 등록했습니다.",AchievementType.COLLECTION_COUNT,3,"SMALL_COLLECTOR");achievement("TOTAL_DISCOVERY_10","관찰의 시작","총 10회 발견했습니다.",AchievementType.TOTAL_DISCOVERY_COUNT,10,"DAILY_OBSERVER");achievement("SAME_OBJECT_5","한 가지에 집중","같은 대상을 5회 발견했습니다.",AchievementType.SAME_OBJECT_COUNT,5,"FOCUSED_OBSERVER");}
	private void title(String code,String name,String description){if(titles.findByCode(code).isEmpty())titles.save(Title.create(code,name,description));}
	private void achievement(String code,String name,String description,AchievementType type,int required,String reward){if(achievements.findByCode(code).isEmpty())achievements.save(Achievement.create(code,name,description,type,required,reward));}
}
