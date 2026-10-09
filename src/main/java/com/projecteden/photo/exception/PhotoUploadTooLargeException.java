package com.projecteden.photo.exception;

public class PhotoUploadTooLargeException extends RuntimeException {
	public PhotoUploadTooLargeException() {
		super("사진 파일은 1 MiB 이하여야 합니다.");
	}
}
