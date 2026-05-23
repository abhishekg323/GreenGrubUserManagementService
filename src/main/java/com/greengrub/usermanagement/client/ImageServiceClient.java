package com.greengrub.usermanagement.client;

import com.google.protobuf.ByteString;
import com.greengrub.proto.image.CreatorType;
import com.greengrub.proto.image.GetImagesByCreatorResponse;
import com.greengrub.proto.image.Image;
import com.greengrub.proto.image.ImageByImageIdRequest;
import com.greengrub.proto.image.ImageServiceGrpc;
import com.greengrub.proto.image.UploadImagesRequest;
import com.greengrub.proto.image.UploadImagesResponse;
import com.greengrub.usermanagement.exception.ImageServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous wrapper around the image-service gRPC contract. Uploads are
 * Resilience4j-wrapped and surface failures as {@link ImageServiceException}
 * (→ 503). Reads degrade silently to an empty Optional so a missing/late image
 * never breaks a user GET.
 *
 * <p>Uses two separate Resilience4j instances (imageServiceRetry /
 * imageServiceBreaker) so a dead image-service does not trip the userBreaker
 * (and vice versa).
 */
@Component
@Slf4j
public class ImageServiceClient {

    private static final String CHANNEL_NAME = "image-service";
    private static final long UPLOAD_DEADLINE_SECONDS = 5;
    private static final long READ_DEADLINE_SECONDS = 2;

    private final ImageServiceGrpc.ImageServiceBlockingStub stub;

    public ImageServiceClient(GrpcChannelFactory channelFactory) {
        this.stub = ImageServiceGrpc.newBlockingStub(channelFactory.createChannel(CHANNEL_NAME));
    }

    /**
     * Upload a single profile image owned by the given user. Returns the new
     * image id assigned by image-service.
     */
    @Retry(name = "imageServiceRetry")
    @CircuitBreaker(name = "imageServiceBreaker")
    public String uploadProfileImage(String userId, byte[] data, String fileName, String contentType) {
        try {
            UploadImagesRequest request = UploadImagesRequest.newBuilder()
                    .setCreatorId(userId)
                    .setCreatorType(CreatorType.CUSTOMER)
                    .addImageData(ByteString.copyFrom(data))
                    .setFileName(fileName)
                    .setContentType(contentType)
                    .build();

            UploadImagesResponse resp = stub
                    .withDeadlineAfter(UPLOAD_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .uploadImages(request);

            if (resp.getMessageCount() == 0) {
                throw new ImageServiceException("image-service returned an empty upload response", null);
            }
            // image-service returns the new image id(s) in `message`; first entry is ours.
            return resp.getMessage(0);
        } catch (StatusRuntimeException e) {
            log.error("image-service upload failed for userId {}: {}", userId, e.getStatus());
            throw new ImageServiceException("image-service upload failed: " + e.getStatus(), e);
        }
    }

    /**
     * Look up a single image by id. Failures (network, deadline, NOT_FOUND) are
     * absorbed and returned as {@link Optional#empty()} — a stale/missing image
     * pointer must not break user reads.
     */
    public Optional<ImageView> getById(String imageId) {
        try {
            Iterator<GetImagesByCreatorResponse> it = stub
                    .withDeadlineAfter(READ_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getImageByImageId(ImageByImageIdRequest.newBuilder().setImageId(imageId).build());

            if (!it.hasNext()) {
                return Optional.empty();
            }
            GetImagesByCreatorResponse first = it.next();
            if (first.getImagesCount() == 0) {
                return Optional.empty();
            }
            Image img = first.getImages(0);
            return Optional.of(new ImageView(img.getImageId(), img.getImageUrl()));
        } catch (StatusRuntimeException e) {
            log.warn("Failed to fetch image {} from image-service: {}", imageId, e.getStatus());
            return Optional.empty();
        }
    }

    /**
     * Slim view of an image returned by image-service — id + resolved URL only.
     */
    public record ImageView(String imageId, String imageUrl) {}
}
