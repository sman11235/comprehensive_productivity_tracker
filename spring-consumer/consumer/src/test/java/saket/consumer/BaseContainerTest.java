package saket.consumer;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseContainerTest {
    // Spin up a Postgres 16 container with PostGIS pre-installed
    // Note: You must use a postgis image, not standard postgres
    private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.4-alpine")
          .asCompatibleSubstituteFor("postgres");

    //containers framework automatically closes the closeable.
    @SuppressWarnings("resource")
    @ServiceConnection
    protected static final PostgreSQLContainer postgis = 
        new PostgreSQLContainer(
            POSTGIS_IMAGE
        ).withDatabaseName("testDb")
         .withUsername("appDb")
         .withPassword("appDb")
         .withInitScript("01-init.sql");
}
