package com.studp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoucherCreateMessage {
    Long voucherId;

    Long userId;

    Long orderId;
}
