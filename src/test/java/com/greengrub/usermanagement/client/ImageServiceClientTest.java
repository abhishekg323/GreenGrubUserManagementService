package com.greengrub.usermanagement.client;

import com.google.protobuf.ByteString;
import com.greengrub.proto.image.*;
import com.greengrub.usermanagement.exception.ImageServiceException;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceClientTest {

    @Mock
    private ImageServiceGrpc.ImageServiceBlockingStub mockStub;

    private ImageServiceClient client;

    @BeforeEach
    void setUp() {
        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        ManagedChannel dummyChannel = mock(ManagedChannel.class);
        when(channelFactory.createChannel(anyString())).thenReturn(dummyChannel);
        client = new ImageServiceClient(channelFactory);
        ReflectionTestUtils.setField(client, "stub", mockStub);
    }

    // ── uploadProfileImage ────────────────────────────────────────────────────

    @Test
    void uploadProfileImage_success_returnsImageId() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        UploadImagesResponse resp = UploadImagesResponse.newBuilder()
                .addMessage("img-001")
                .build();
        when(withDeadlineStub.uploadImages(any(UploadImagesRequest.class))).thenReturn(resp);

        String result = client.uploadProfileImage("user-001", new byte[]{1, 2, 3}, "photo.jpg", "image/jpeg");

        assertThat(result).isEqualTo("img-001");
    }

    @Test
    void uploadProfileImage_emptyResponse_throwsImageServiceException() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        UploadImagesResponse resp = UploadImagesResponse.newBuilder().build(); // no messages
        when(withDeadlineStub.uploadImages(any())).thenReturn(resp);

        assertThatThrownBy(() -> client.uploadProfileImage("user-001", new byte[]{1}, "f.jpg", "image/jpeg"))
                .isInstanceOf(ImageServiceException.class)
                .hasMessageContaining("empty upload response");
    }

    @Test
    void uploadProfileImage_grpcFailure_throwsImageServiceException() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);
        when(withDeadlineStub.uploadImages(any())).thenThrow(
                new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> client.uploadProfileImage("user-001", new byte[]{1}, "f.jpg", "image/jpeg"))
                .isInstanceOf(ImageServiceException.class);
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_success_returnsImageView() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        Image img = Image.newBuilder().setImageId("img-001").setImageUrl("https://cdn/img-001.jpg").build();
        GetImagesByCreatorResponse chunk = GetImagesByCreatorResponse.newBuilder().addImages(img).build();
        Iterator<GetImagesByCreatorResponse> it = Collections.singletonList(chunk).iterator();
        when(withDeadlineStub.getImageByImageId(any())).thenReturn(it);

        Optional<ImageServiceClient.ImageView> result = client.getById("img-001");

        assertThat(result).isPresent();
        assertThat(result.get().imageId()).isEqualTo("img-001");
        assertThat(result.get().imageUrl()).isEqualTo("https://cdn/img-001.jpg");
    }

    @Test
    void getById_emptyIterator_returnsEmpty() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        Iterator<GetImagesByCreatorResponse> it = Collections.<GetImagesByCreatorResponse>emptyList().iterator();
        when(withDeadlineStub.getImageByImageId(any())).thenReturn(it);

        Optional<ImageServiceClient.ImageView> result = client.getById("img-999");

        assertThat(result).isEmpty();
    }

    @Test
    void getById_chunkHasNoImages_returnsEmpty() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        GetImagesByCreatorResponse emptyChunk = GetImagesByCreatorResponse.newBuilder().build();
        Iterator<GetImagesByCreatorResponse> it = Collections.singletonList(emptyChunk).iterator();
        when(withDeadlineStub.getImageByImageId(any())).thenReturn(it);

        Optional<ImageServiceClient.ImageView> result = client.getById("img-001");

        assertThat(result).isEmpty();
    }

    @Test
    void getById_grpcFailure_returnsEmpty() {
        ImageServiceGrpc.ImageServiceBlockingStub withDeadlineStub = mock(ImageServiceGrpc.ImageServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);
        when(withDeadlineStub.getImageByImageId(any())).thenThrow(
                new StatusRuntimeException(Status.NOT_FOUND));

        Optional<ImageServiceClient.ImageView> result = client.getById("img-001");

        assertThat(result).isEmpty();
    }
}
