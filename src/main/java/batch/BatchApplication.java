package batch;

import batch.service.LogProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@Slf4j
@SpringBootApplication(scanBasePackages = {"batch.*"})
@MapperScan(basePackages = {"batch.mapper"})
@RequiredArgsConstructor
public class BatchApplication implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final LogProcessService service;

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BatchApplication.class);
        application.setDefaultProperties(Map.of("spring.profiles.active", "dev"));
        application.run(args);
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            log.info("{} Application Started", LogProcessService.class.getSimpleName());
            service.auditLogParse();
            log.info("{} Application Completed", LogProcessService.class.getSimpleName());
        } catch (Exception e) {
            /*
            Send Alarm (Slack, SMS, EMAIL...)
            */
            log.error("{} Application Failed", LogProcessService.class.getSimpleName(), e);
            throw new RuntimeException(e);
        }
    }
}
