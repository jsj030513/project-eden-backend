package com.projecteden.imagenormalization;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eden.image-normalization")
public class ImageNormalizationProperties {

	private boolean enabled = true;
	private long encodedMaxBytes = 15L * 1024 * 1024;
	private long providerMaxBytes = 10L * 1024 * 1024;
	private int maxWidth = 8192;
	private int maxHeight = 8192;
	private long maxPixels = 24_000_000L;
	private int maxOutputWidth = 2560;
	private int maxOutputHeight = 2560;
	private float jpegQuality = 0.88f;
	private int maxFramesToProbe = 20;
	private long outputMaxBytes = 10L * 1024 * 1024;

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public long getEncodedMaxBytes() { return encodedMaxBytes; }
	public void setEncodedMaxBytes(long encodedMaxBytes) { this.encodedMaxBytes = encodedMaxBytes; }
	public long getProviderMaxBytes() { return providerMaxBytes; }
	public void setProviderMaxBytes(long providerMaxBytes) { this.providerMaxBytes = providerMaxBytes; }
	public int getMaxWidth() { return maxWidth; }
	public void setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; }
	public int getMaxHeight() { return maxHeight; }
	public void setMaxHeight(int maxHeight) { this.maxHeight = maxHeight; }
	public long getMaxPixels() { return maxPixels; }
	public void setMaxPixels(long maxPixels) { this.maxPixels = maxPixels; }
	public int getMaxOutputWidth() { return maxOutputWidth; }
	public void setMaxOutputWidth(int maxOutputWidth) { this.maxOutputWidth = maxOutputWidth; }
	public int getMaxOutputHeight() { return maxOutputHeight; }
	public void setMaxOutputHeight(int maxOutputHeight) { this.maxOutputHeight = maxOutputHeight; }
	public float getJpegQuality() { return jpegQuality; }
	public void setJpegQuality(float jpegQuality) { this.jpegQuality = jpegQuality; }
	public int getMaxFramesToProbe() { return maxFramesToProbe; }
	public void setMaxFramesToProbe(int maxFramesToProbe) { this.maxFramesToProbe = maxFramesToProbe; }
	public long getOutputMaxBytes() { return outputMaxBytes; }
	public void setOutputMaxBytes(long outputMaxBytes) { this.outputMaxBytes = outputMaxBytes; }
}
