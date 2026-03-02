package saket.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class ConsumerApplicationTests extends BaseContainerTest {

	@Test
	void contextLoads() {
	}

	@Test
	void getFromDB() {
		
	}

}
