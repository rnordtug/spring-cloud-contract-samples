package com.example;

//remove::start[]

import net.devh.boot.grpc.server.config.GrpcServerProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.http.HttpVerifier;
import org.springframework.cloud.contract.verifier.http.OkHttpHttpVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

//end::start[]
/*import org.lognet.springboot.grpc.autoconfigure.GRpcAutoConfiguration;
import org.lognet.springboot.grpc.autoconfigure.GRpcServerProperties;
import org.lognet.springboot.grpc.context.LocalRunningGrpcPort;*/

@SpringBootTest(classes = BeerRestBase.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"grpc.server.port=0",
//				"grpc.inProcessServerName=test", // Enable inProcess server
//				"grpc.enabled=false", // Disable external server
		})
//@ExtendWith(GrpcCleanupExtension.class)
public abstract class BeerRestBase {
	//remove::start[]

	@Autowired GrpcServerProperties properties;

	/*@BeforeEach
	void setup(Resources resources) {
		resources.register(InProcessServerBuilder.forName(properties.getInProcessServerName()).directExecutor().build(), Duration.ofSeconds(5));
	}*/

	@Configuration
	@Import(TestConfig.class)
//	@ImportAutoConfiguration(GRpcAutoConfiguration.class)
	@EnableAutoConfiguration
	static class Config {

		@Bean
		ProducerController producerController(PersonCheckingService personCheckingService) {
			return new ProducerController(personCheckingService);
		}

		@Bean
		PersonCheckingService testPersonCheckingService() {
			return argument -> argument.getAge() >= 20;
		}

	}

	//remove::end[]
	@Configuration
	static class TestConfig {

		//	@Bean
		HttpVerifier grpcHttpVerifier(GrpcServerProperties properties) {
			/*return new GrpcStubHttpVerifier(InProcessChannelBuilder.forName(properties.getInProcessServerName()).directExecutor().build());*/
			return null;
		}

		@Bean
		HttpVerifier httpOkVerifier(GrpcServerProperties properties) {
			return new OkHttpHttpVerifier("http://localhost:" + properties.getPort());
		}

	}

}



