package com.hrms.domain.vendor.mapper;

import com.hrms.domain.vendor.dto.VendorResponse;
import com.hrms.domain.vendor.entity.Vendor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VendorMapper {
    VendorResponse toResponse(Vendor vendor);
}
