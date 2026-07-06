package com.projecteden.notification.scheduler;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.projecteden.notification.service.NotificationService;

@Component
@Profile("!test")
public class DailyPromptScheduler {

	private final NotificationService notificationService;

	public DailyPromptScheduler(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
	public void createDailyPrompts() {
		notificationService.createDailyPrompts();
	}
}
