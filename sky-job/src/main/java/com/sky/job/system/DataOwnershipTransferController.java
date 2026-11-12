package com.sky.job.system;

import com.sky.job.security.AuthenticatedUser;
import com.sky.job.system.dto.DataOwnershipTransferRequest;
import com.sky.job.system.dto.DataOwnershipTransferResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/data-ownership")
public class DataOwnershipTransferController {
    private final DataOwnershipTransferService service;

    public DataOwnershipTransferController(DataOwnershipTransferService service) {
        this.service = service;
    }

    @PostMapping("/transfer")
    public DataOwnershipTransferResponse transfer(
            @RequestBody DataOwnershipTransferRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return service.transfer(request, user, httpRequest);
    }
}
