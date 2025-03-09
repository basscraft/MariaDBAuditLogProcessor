package batch.service;

import batch.mapper.ServerAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogProcessService {

    private final ServerAuditMapper serverAuditMapper;
    private StopWatch stopWatch;

    @Value("${path.log.audit}")
    private String auditLogPath;


    public void test() {
        List<Map<String, String>> list = serverAuditMapper.selectTest(1);
        log.debug("{}", list);
    }

    public void auditLogParse() {
        stopWatch.start();


        stopWatch.stop();
    }
}
