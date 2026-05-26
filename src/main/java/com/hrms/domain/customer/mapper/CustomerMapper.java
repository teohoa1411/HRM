package com.hrms.domain.customer.mapper;

import com.hrms.domain.customer.dto.CustomerPriceResponse;
import com.hrms.domain.customer.dto.CustomerResponse;
import com.hrms.domain.customer.entity.Customer;
import com.hrms.domain.customer.entity.CustomerPrice;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getCompanyId(), c.getCode(), c.getVendorCode(), c.getName(),
                c.getContactPerson(), c.getContactEmail(), c.getContactPhone(),
                c.getAddress(), c.getTaxCode(), c.getNotes(), c.isActive(), c.getCreatedAt()
        );
    }

    public CustomerPriceResponse toPriceResponse(CustomerPrice p, String jobRoleName) {
        return new CustomerPriceResponse(
                p.getId(), p.getCustomerId(), p.getJobRoleId(), jobRoleName,
                p.getPrice(), p.getPriceUnit(), p.getCurrency(),
                p.getEffectiveFrom(), p.getEffectiveTo(), p.getNotes(), p.getCreatedAt()
        );
    }
}
