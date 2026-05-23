package com.greengrub.usermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slim JSON view of donation-service's DonationListResponse. We don't expose
 * the proto types directly to HTTP clients — protobuf-generated classes do
 * not serialize well to JSON (default-valued primitives leak, enums become
 * ints, etc.) and they pin the wire shape to our public API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationListView {

    private List<DonationView> donations;
    private int totalCount;
    private int page;
    private int pageSize;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DonationView {
        private String id;
        private String donationName;
        private String pickUpAddress;
        private String pickUpTime;
        private QuantityView estimatedQuantity;
        private List<String> foodItemsId;
        private String status;
        private String creationDate;
        private String updateDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuantityView {
        private double amount;
        private String unit;
    }
}
