/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.support.DefaultMessage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.converter.YamlContract;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifier;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessage;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessaging;
import org.springframework.cloud.contract.verifier.util.ContractVerifierUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StringUtils;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = {TestConfig.class, Application.class})
@Testcontainers
@AutoConfigureMessageVerifier
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BaseClass {

	private static final Logger log = LoggerFactory.getLogger(BaseClass.class);

	@Container
	static RabbitMQContainer rabbit = new RabbitMQContainer();

	@DynamicPropertySource
	static void rabbitProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
		registry.add("camel.component.rabbitmq.port-number", rabbit::getAmqpPort);
	}

	@Autowired
	Controller controller;
	@Autowired
	RabbitMessageVerifier rabbitMessageVerifier;
	@Autowired
	CamelContext camelContext;

	@BeforeEach
	public void setup(TestInfo testInfo) {
		setupMessagingFromContract(testInfo);
	}

	private void setupMessagingFromContract(TestInfo testInfo) {
		try {
			YamlContract contract = ContractVerifierUtil.contract(this, testInfo.getDisplayName());
			setupMessagingIfPresent(contract);
		}
		catch (Exception e) {
			log.warn("An exception occurred while trying to setup messaging from contract", e);
		}
		this.camelContext.getShutdownStrategy().setTimeout(1);
	}

	private void setupMessagingIfPresent(YamlContract contract) {
		if (contract.input == null && contract.outputMessage == null) {
			return;
		}
		if (contract.input != null && StringUtils.hasText(contract.input.messageFrom)) {
			setupConnection(contract.input.messageFrom, contract);
		}
		if (contract.outputMessage != null && StringUtils.hasText(contract.outputMessage.sentTo)) {
			setupConnection(contract.outputMessage.sentTo, contract);
		}
	}

	private void setupConnection(String destination, YamlContract contract) {
		if (StringUtils.isEmpty(destination)) {
			return;
		}
		log.info("Setting up destination [{}]", destination);
		this.rabbitMessageVerifier.receive(destination, 100, TimeUnit.MILLISECONDS, contract);
	}

	public void trigger() {
		this.controller.sendFoo("example");
	}
}

@Configuration
class TestConfig {

	@Bean
	RabbitMessageVerifier rabbitTemplateMessageVerifier(ConsumerTemplate consumerTemplate, ProducerTemplate producerTemplate) {
		return new RabbitMessageVerifier(consumerTemplate, producerTemplate);
	}

	@Bean
	ContractVerifierMessaging<Message> rabbitContractVerifierMessaging(RabbitMessageVerifier messageVerifier) {
		return new ContractVerifierMessaging<Message>(messageVerifier) {

			@Override
			protected ContractVerifierMessage convert(Message receive) {
				if (receive == null) {
					return null;
				}
				return new ContractVerifierMessage(receive.getBody(), receive.getHeaders());
			}

		};
	}

}

class RabbitMessageVerifier implements MessageVerifier<Message> {

	private static final Logger log = LoggerFactory.getLogger(RabbitMessageVerifier.class);

	private final ConsumerTemplate consumerTemplate;

	private final ProducerTemplate producerTemplate;

	RabbitMessageVerifier(ConsumerTemplate consumerTemplate, ProducerTemplate producerTemplate) {
		this.consumerTemplate = consumerTemplate;
		this.producerTemplate = producerTemplate;
	}

	@Override
	public Message receive(String destination, long timeout, TimeUnit timeUnit, @Nullable YamlContract contract) {
		Exchange receive = consumerTemplate.receive(rabbitUrl(destination), timeUnit.toMillis(timeout));
		log.info("Received a message! [{}]", receive);
		if (receive == null) {
			return null;
		}
		return receive.getIn();
	}

	@NotNull
	private String rabbitUrl(String destination) {
		String url = "rabbitmq://" + destination + "?queue=" + destination + "&routingKey=#";
		log.info("Rabbit URL [{}]", url);
		return url;
	}

	@Override
	public Message receive(String destination, YamlContract contract) {
		return receive(destination, 1, TimeUnit.SECONDS, contract);
	}

	@Override
	public void send(Message message, String destination, @Nullable YamlContract contract) {
		Exchange exchange = ExchangeBuilder.anExchange(producerTemplate.getCamelContext()).build();
		exchange.setIn(message);
		producerTemplate.send(rabbitUrl(destination), exchange);
		log.info("Sent a message! [{}]", exchange);
	}

	@Override
	public void send(Object payload, Map headers, String destination, @Nullable YamlContract contract) {
		DefaultMessage defaultMessage = new DefaultMessage(producerTemplate.getCamelContext());
		defaultMessage.setHeaders(headers);
		defaultMessage.setBody(payload);
		send(defaultMessage, destination, contract);
	}
}
