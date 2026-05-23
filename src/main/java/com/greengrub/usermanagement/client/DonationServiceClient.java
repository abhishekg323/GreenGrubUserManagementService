package com.greengrub.usermanagement.client;

import com.greengrub.proto.donation.DonationByUserIdRequest;
import com.greengrub.proto.donation.DonationListResponse;
import com.greengrub.proto.donation.DonationServiceGrpc;
import com.greengrub.usermanagement.exception.DonationServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Synchronous wrapper around the donation-service gRPC contract. Failures
 * surface as {@link DonationServiceException} (→ 503) rather than degrading
 * silently — an empty list when donation-service is sick would be a lie, so
 * we'd rather fail fast and let the caller decide whether to retry.
 *
 * <p>Uses its own Resilience4j instances (donationServiceRetry /
 * donationServiceBreaker) so a dead donation-service does not trip the user
 * or image breakers.
 */
@Component
@Slf4j
public class DonationServiceClient {

    private static final String CHANNEL_NAME = "donation-service";
    private static final long READ_DEADLINE_SECONDS = 3;

    private final DonationServiceGrpc.DonationServiceBlockingStub stub;

    public DonationServiceClient(GrpcChannelFactory channelFactory) {
        this.stub = DonationServiceGrpc.newBlockingStub(channelFactory.createChannel(CHANNEL_NAME));
    }

    /**
     * Fetch a paginated list of donations created by the given user. Pagination
     * is enforced server-side by donation-service; this client just forwards
     * the page / pageSize the controller resolved.
     */
    @Retry(name = "donationServiceRetry")
    @CircuitBreaker(name = "donationServiceBreaker")
    public DonationListResponse getDonationsByUserId(String userId, int page, int pageSize) {
        try {
            DonationByUserIdRequest request = DonationByUserIdRequest.newBuilder()
                    .setUserId(userId)
                    .setPage(page)
                    .setPageSize(pageSize)
                    .build();

            return stub
                    .withDeadlineAfter(READ_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getDonationsByUserId(request);
        } catch (StatusRuntimeException e) {
            log.error("donation-service GetDonationsByUserId failed for userId {}: {}", userId, e.getStatus());
            throw new DonationServiceException(
                    "donation-service GetDonationsByUserId failed: " + e.getStatus(), e);
        }
    }
}
