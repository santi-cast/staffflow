package com.staffflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class StaffflowBackendApplication {

	public static void main(String[] args) {

      SpringApplication.run(StaffflowBackendApplication.class, args);
	}

}
