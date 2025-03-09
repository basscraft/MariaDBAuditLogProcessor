package batch.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ServerAuditMapper {
    List<Map<String,String>> selectTest(int seq);
}
