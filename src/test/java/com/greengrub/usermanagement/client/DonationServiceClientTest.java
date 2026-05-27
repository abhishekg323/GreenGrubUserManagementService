package com.greengrub.usermanagement.client;

import com.greengrub.proto.donation.*;
import com.greengrub.usermanagement.exception.DonationServiceException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationServiceClientTest {

    @Mock
    private DonationServiceGrpc.DonationServiceBlockingStub mockStub;

    private DonationServiceClient client;

    @BeforeEach
    void setUp() {
        GrpcChannelFactory channelFactory = mock(GrpcChannelFactory.class);
        ManagedChannel dummyChannel = mock(ManagedChannel.class);
        when(channelFactory.createChannel(anyString())).thenReturn(dummyChannel);
        client = new DonationServiceClient(channelFactory);
        ReflectionTestUtils.setField(client, "stub", mockStub);
    }

    @Test
    void getDonationsByUserId_success_returnsDonationListResponse() {
        DonationServiceGrpc.DonationServiceBlockingStub withDeadlineStub =
                mock(DonationServiceGrpc.DonationServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        DonationListResponse resp = DonationListResponse.newBuilder()
                .setTotalCount(2)
                .setPage(0)
                .setPageSize(10)
                .build();
        when(withDeadlineStub.getDonationsByUserId(any(DonationByUserIdRequest.class))).thenReturn(resp);

        DonationListResponse result = client.getDonationsByUserId("user-001", 0, 10);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(0);
    }

    @Test
    void getDonationsByUserId_grpcFailure_throwsDonationServiceException() {
        DonationServiceGrpc.DonationServiceBlockingStub withDeadlineStub =
                mock(DonationServiceGrpc.DonationServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);
        when(withDeadlineStub.getDonationsByUserId(any())).thenThrow(
                new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> client.getDonationsByUserId("user-001", 0, 10))
                .isInstanceOf(DonationServiceException.class);
    }

    @Test
    void getDonationsByUserId_emptyResult_returnEmptyResponse() {
        DonationServiceGrpc.DonationServiceBlockingStub withDeadlineStub =
                mock(DonationServiceGrpc.DonationServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        DonationListResponse resp = DonationListResponse.newBuilder()
                .setTotalCount(0).setPage(0).setPageSize(10).build();
        when(withDeadlineStub.getDonationsByUserId(any())).thenReturn(resp);

        DonationListResponse result = client.getDonationsByUserId("user-001", 0, 10);

        assertThat(result.getTotalCount()).isZero();
    }

    @Test
    void getDonationsByUserId_deadlineExceeded_throwsDonationServiceException() {
        DonationServiceGrpc.DonationServiceBlockingStub withDeadlineStub =
                mock(DonationServiceGrpc.DonationServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);
        when(withDeadlineStub.getDonationsByUserId(any())).thenThrow(
                new StatusRuntimeException(Status.DEADLINE_EXCEEDED));

        assertThatThrownBy(() -> client.getDonationsByUserId("user-001", 0, 10))
                .isInstanceOf(DonationServiceException.class)
                .hasMessageContaining("DEADLINE_EXCEEDED");
    }

    @Test
    void getDonationsByUserId_forwardsPageAndPageSize() {
        DonationServiceGrpc.DonationServiceBlockingStub withDeadlineStub =
                mock(DonationServiceGrpc.DonationServiceBlockingStub.class);
        when(mockStub.withDeadlineAfter(anyLong(), any())).thenReturn(withDeadlineStub);

        DonationListResponse resp = DonationListResponse.newBuilder().setPage(2).setPageSize(5).build();
        when(withDeadlineStub.getDonationsByUserId(argThat(r -> r.getPage() == 2 && r.getPageSize() == 5)))
                .thenReturn(resp);

        DonationListResponse result = client.getDonationsByUserId("user-001", 2, 5);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(5);
    }
}
