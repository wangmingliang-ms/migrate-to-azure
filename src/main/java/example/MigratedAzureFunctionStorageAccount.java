/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package example;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigratedAzureFunctionStorageAccount {
    private static final float MAX_DIMENSION = 100;
    private final String JPG_TYPE = "jpg";
    private final String PNG_TYPE = "png";

    @FunctionName("ImageResizeFunction")
    public void run(
        @BlobTrigger(name = "blob", dataType = "binary", path = "input-container/{name}") byte[] content,
        @BindingName("name") String name,
        final ExecutionContext context) {
        try {
            String srcBucket = "input-container";
            String srcKey = name;
            String dstKey = "resized-" + srcKey;

            String imageType = inferImageType(srcKey);
            if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
                context.getLogger().info("Skipping non-image " + srcKey);
                return;
            }

            BufferedImage srcImage = ImageIO.read(new ByteArrayInputStream(content));
            BufferedImage newImage = resizeImage(srcImage);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(newImage, imageType, outputStream);

            uploadToBlobStorage(outputStream.toByteArray(), dstKey, context);

            context.getLogger().info("Successfully resized " + srcBucket + "/"
                + srcKey + " and uploaded to output-container/" + dstKey);
        } catch (IOException e) {
            context.getLogger().severe(e.getMessage());
        }
    }

    private String inferImageType(String srcKey) {
        String REGEX = ".*\\.([^.]*)";
        Matcher matcher = Pattern.compile(REGEX).matcher(srcKey);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    private void uploadToBlobStorage(byte[] data, String dstKey, ExecutionContext context) {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
            .connectionString("your-storage-connection-string")
            .buildClient();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("output-container");
        BlobClient blobClient = containerClient.getBlobClient(dstKey);
        blobClient.upload(new ByteArrayInputStream(data), data.length);
    }

    private BufferedImage resizeImage(BufferedImage srcImage) {
        int srcHeight = srcImage.getHeight();
        int srcWidth = srcImage.getWidth();
        // Infer scaling factor to avoid stretching image unnaturally
        float scalingFactor = Math.min(MAX_DIMENSION / srcWidth, MAX_DIMENSION / srcHeight);
        int width = (int) (scalingFactor * srcWidth);
        int height = (int) (scalingFactor * srcHeight);

        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        // Fill with white before applying semi-transparent (alpha) images
        graphics.setPaint(Color.white);
        graphics.fillRect(0, 0, width, height);
        // Simple bilinear resize
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(srcImage, 0, 0, width, height, null);
        graphics.dispose();
        return resizedImage;
    }
}