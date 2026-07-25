package com.example.ratelimiter.controller;

import com.example.ratelimiter.dto.AllowRequestDto;
import com.example.ratelimiter.dto.AllowResponseDto;
import com.example.ratelimiter.dto.BucketStateDto;
import com.example.ratelimiter.exception.UnknownUserException;
import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;
import com.example.ratelimiter.service.RateLimiterService;
import com.example.ratelimiter.util.TimeUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class RateLimiterController {

    private final RateLimiterService service;

    public RateLimiterController(RateLimiterService service) {
        this.service = service;
    }

    @PostMapping("/requests")
    public ResponseEntity<AllowResponseDto> checkRequest(@Valid @RequestBody AllowRequestDto request) {
        log.info("Incoming allow-request: userId={}", request.getUserId());

        double timestamp = request.getTimestamp() != null
                ? request.getTimestamp()
                : TimeUtil.nowEpochSeconds();

        AllowResult result = service.allowRequest(request.getUserId(), timestamp);
        BucketStateDto bucket = BucketStateDto.from(result.newState().buckets().get(request.getUserId()));
        AllowResponseDto body = new AllowResponseDto(result.allowed(), bucket);

        log.info("Allow-request result: userId={}, allowed={}", request.getUserId(), result.allowed());

        HttpStatus status = result.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/users/{userId}/bucket")
    public BucketStateDto getBucket(@PathVariable String userId) {
        Bucket bucket = service.getBucketState(userId);
        if (bucket == null) {
            throw new UnknownUserException(userId);
        }
        return BucketStateDto.from(bucket);
    }
}
