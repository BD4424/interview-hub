package com.prephub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the background embedding indexer. Disabled in tests via prephub.dedup.index-interval-ms. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
