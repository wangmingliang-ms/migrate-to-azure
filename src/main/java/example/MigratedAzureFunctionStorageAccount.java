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
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobOutput;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
        @BlobOutput(name = "resizedBlob", dataType = "binary", path = "output-container/{name}") OutputBinding<byte[]> resizedBlob,
        final ExecutionContext context
    ) {
        try {
            String srcKey = name;
            String dstKey = "resized-" + srcKey;

            // Infer the image type.
            String REGEX = ".*\\.([^.]*)";
            Matcher matcher = Pattern.compile(REGEX).matcher(srcKey);
            if (!matcher.matches()) {
                context.getLogger().info("Unable to infer image type for key " + srcKey);
                return;
            }
            String imageType = matcher.group(1);
            if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
                context.getLogger().info("Skipping non-image " + srcKey);
                return;
            }

            // Read the source image and resize it
            InputStream s3Object = new ByteArrayInputStream(content);
            BufferedImage srcImage = ImageIO.read(s3Object);
            BufferedImage newImage = resizeImage(srcImage);

            // Re-encode image to target format
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(newImage, imageType, outputStream);
            byte[] resizedContent = outputStream.toByteArray();

            // Upload new image to Azure Blob Storage
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(System.getenv("AzureWebJobsStorage"))
                .buildClient();

            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("output-container");
            BlobClient blobClient = containerClient.getBlobClient(dstKey);
            blobClient.upload(new ByteArrayInputStream(resizedContent), resizedContent.length);

            context.getLogger().info("Successfully resized " + srcKey + " and uploaded to " + dstKey);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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