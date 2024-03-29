package ru.gadjini.telegram.unzipper.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import ru.gadjini.telegram.smart.bot.commons.dao.QueueDao;
import ru.gadjini.telegram.smart.bot.commons.dao.WorkQueueDaoDelegate;
import ru.gadjini.telegram.smart.bot.commons.domain.DownloadQueueItem;
import ru.gadjini.telegram.smart.bot.commons.domain.QueueItem;
import ru.gadjini.telegram.smart.bot.commons.domain.TgFile;
import ru.gadjini.telegram.smart.bot.commons.property.FileLimitProperties;
import ru.gadjini.telegram.smart.bot.commons.service.concurrent.SmartExecutorService;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;
import ru.gadjini.telegram.smart.bot.commons.utils.JdbcUtils;
import ru.gadjini.telegram.unzipper.domain.UnzipQueueItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class UnzipQueueDao implements WorkQueueDaoDelegate<UnzipQueueItem> {

    private JdbcTemplate jdbcTemplate;

    private FileLimitProperties fileLimitProperties;

    private ObjectMapper objectMapper;

    @Autowired
    public UnzipQueueDao(JdbcTemplate jdbcTemplate, FileLimitProperties fileLimitProperties,
                         ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileLimitProperties = fileLimitProperties;
        this.objectMapper = objectMapper;
    }

    public int create(UnzipQueueItem unzipQueueItem) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        if (unzipQueueItem.getItemType() == UnzipQueueItem.ItemType.UNZIP) {
            jdbcTemplate.update(
                    con -> {
                        PreparedStatement ps = con.prepareStatement("INSERT INTO unzip_queue(user_id, file, type, status, item_type) " +
                                "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

                        ps.setInt(1, unzipQueueItem.getUserId());
                        ps.setObject(2, unzipQueueItem.getFile().sqlObject());
                        ps.setString(3, unzipQueueItem.getType().name());
                        ps.setInt(4, unzipQueueItem.getStatus().getCode());
                        ps.setInt(5, unzipQueueItem.getItemType().getCode());

                        return ps;
                    },
                    keyHolder
            );
        } else if (unzipQueueItem.getItemType() == UnzipQueueItem.ItemType.EXTRACT_FILE) {
            jdbcTemplate.update(
                    con -> {
                        PreparedStatement ps = con.prepareStatement("INSERT INTO unzip_queue(user_id, extract_file_id, progress_message_id, status, item_type, extract_file_size) " +
                                "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

                        ps.setInt(1, unzipQueueItem.getUserId());
                        ps.setObject(2, unzipQueueItem.getExtractFileId());
                        ps.setInt(3, unzipQueueItem.getProgressMessageId());
                        ps.setInt(4, unzipQueueItem.getStatus().getCode());
                        ps.setInt(5, unzipQueueItem.getItemType().getCode());
                        ps.setLong(6, unzipQueueItem.getExtractFileSize());

                        return ps;
                    },
                    keyHolder
            );
        } else {
            jdbcTemplate.update(
                    con -> {
                        PreparedStatement ps = con.prepareStatement("INSERT INTO unzip_queue(user_id, progress_message_id, status, item_type, extract_file_size) " +
                                "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

                        ps.setInt(1, unzipQueueItem.getUserId());
                        ps.setInt(2, unzipQueueItem.getProgressMessageId());
                        ps.setInt(3, unzipQueueItem.getStatus().getCode());
                        ps.setInt(4, unzipQueueItem.getItemType().getCode());
                        ps.setLong(5, unzipQueueItem.getExtractFileSize());

                        return ps;
                    },
                    keyHolder
            );
        }

        return ((Number) keyHolder.getKeys().get(UnzipQueueItem.ID)).intValue();
    }

    public Integer getQueuePosition(int id, SmartExecutorService.JobWeight weight) {
        return jdbcTemplate.query(
                "SELECT COALESCE(queue_position, 1) as queue_position\n" +
                        "FROM (SELECT id, row_number() over (ORDER BY created_at) AS queue_position\n" +
                        "      FROM unzip_queue \n" +
                        "      WHERE status = 0 AND CASE WHEN item_type IN (1, 2) THEN extract_file_size " +
                        "ELSE (file).size END " + getSign(weight) + " ?\n" +
                        ") as file_q\n" +
                        "WHERE id = ?",
                ps -> {
                    ps.setLong(1, fileLimitProperties.getLightFileMaxWeight());
                    ps.setInt(2, id);
                },
                rs -> {
                    if (rs.next()) {
                        return rs.getInt(QueueItem.QUEUE_POSITION);
                    }

                    return 1;
                }
        );
    }

    @Override
    public List<UnzipQueueItem> poll(SmartExecutorService.JobWeight weight, int limit) {
        return jdbcTemplate.query(
                "WITH r AS (\n" +
                        "    UPDATE unzip_queue SET " + QueueDao.POLL_UPDATE_LIST +
                        " WHERE id IN (SELECT id FROM unzip_queue qu " +
                        "WHERE status = 0 AND NOT EXISTS(select 1 FROM " + DownloadQueueItem.NAME + " dq where dq.producer = 'unzip_queue' AND dq.producer_id = qu.id AND dq.status != 3) " +
                        "AND CASE WHEN item_type = 0 THEN (file).size ELSE " +
                        "extract_file_size END " + getSign(weight) + " ? " + QueueDao.POLL_ORDER_BY + " LIMIT " + limit + ") RETURNING *\n" +
                        ")\n" +
                        "SELECT *, (file).*, 1 as queue_position,\n" +
                        "(SELECT json_agg(ds) FROM (SELECT * FROM " + DownloadQueueItem.NAME + " dq WHERE dq.producer = 'unzip_queue' AND dq.producer_id = r.id) as ds) as downloads\n" +
                        "FROM r",
                ps -> ps.setLong(1, fileLimitProperties.getLightFileMaxWeight()),
                (rs, rowNum) -> map(rs)
        );
    }

    @Override
    public long countReadToComplete(SmartExecutorService.JobWeight weight) {
        return jdbcTemplate.query(
                "SELECT COUNT(id) as cnt\n" +
                        "        FROM unzip_queue qu WHERE qu.status = 0 AND CASE WHEN item_type = 0 THEN " +
                        "(file).size ELSE extract_file_size END " + getSign(weight) + " ?" +
                        " AND NOT EXISTS(select 1 FROM " + DownloadQueueItem.NAME + " dq where dq.producer = 'unzip_queue' AND dq.producer_id = qu.id AND dq.status != 3) ",
                ps -> ps.setLong(1, fileLimitProperties.getLightFileMaxWeight()),
                (rs) -> rs.next() ? rs.getLong("cnt") : 0
        );
    }

    @Override
    public long countProcessing(SmartExecutorService.JobWeight weight) {
        return jdbcTemplate.query(
                "SELECT COUNT(id) as cnt\n" +
                        "        FROM unzip_queue qu WHERE qu.status = 1 AND CASE WHEN item_type = 0 THEN (file).size ELSE " +
                        "extract_file_size END " + getSign(weight) + " ?",
                ps -> {
                    ps.setLong(1, fileLimitProperties.getLightFileMaxWeight());
                },
                (rs) -> rs.next() ? rs.getLong("cnt") : 0
        );
    }

    public SmartExecutorService.JobWeight getWeight(int id) {
        Long size = jdbcTemplate.query(
                "SELECT CASE WHEN item_type IN (1, 2) THEN extract_file_size ELSE (file).size END FROM unzip_queue WHERE id = ?",
                ps -> ps.setInt(1, id),
                rs -> rs.next() ? rs.getLong("size") : null
        );

        return size == null ? null : size > fileLimitProperties.getLightFileMaxWeight() ? SmartExecutorService.JobWeight.HEAVY : SmartExecutorService.JobWeight.LIGHT;
    }

    @Override
    public UnzipQueueItem getById(int id) {
        SmartExecutorService.JobWeight weight = getWeight(id);

        if (weight == null) {
            return null;
        }
        return jdbcTemplate.query(
                "SELECT f.*, (f.file).*, COALESCE(queue_place.queue_position, 1) as queue_position\n" +
                        "FROM unzip_queue f\n" +
                        "         LEFT JOIN (SELECT id, row_number() over (ORDER BY created_at) as queue_position\n" +
                        "                     FROM unzip_queue\n" +
                        "      WHERE status = 0 AND CASE WHEN item_type IN (1, 2) THEN extract_file_size ELSE (file).size END " + getSign(weight) + " ?\n" +
                        ") queue_place ON f.id = queue_place.id\n" +
                        "WHERE f.id = ?\n",
                ps -> {
                    ps.setLong(1, fileLimitProperties.getLightFileMaxWeight());
                    ps.setInt(2, id);
                },
                rs -> {
                    if (rs.next()) {
                        return map(rs);
                    }

                    return null;
                }
        );
    }

    @Override
    public List<UnzipQueueItem> deleteAndGetProcessingOrWaitingByUserId(int userId) {
        return jdbcTemplate.query("WITH r AS (DELETE FROM unzip_queue WHERE user_id = ? RETURNING *) SELECT *, (file).* FROM r",
                ps -> ps.setInt(1, userId),
                (rs, num) -> map(rs)
        );
    }

    @Override
    public UnzipQueueItem deleteAndGetById(int id) {
        return jdbcTemplate.query("WITH del AS(DELETE FROM unzip_queue WHERE id = ? RETURNING *) SELECT *, (file).* FROM del",
                ps -> ps.setInt(1, id),
                rs -> rs.next() ? map(rs) : null
        );
    }

    @Override
    public String getProducerName() {
        return UnzipQueueItem.NAME;
    }

    @Override
    public String getQueueName() {
        return UnzipQueueItem.NAME;
    }

    private String getSign(SmartExecutorService.JobWeight weight) {
        return weight.equals(SmartExecutorService.JobWeight.LIGHT) ? "<=" : ">";
    }

    private UnzipQueueItem map(ResultSet resultSet) throws SQLException {
        Set<String> columns = JdbcUtils.getColumnNames(resultSet.getMetaData());

        UnzipQueueItem item = new UnzipQueueItem();
        item.setId(resultSet.getInt(UnzipQueueItem.ID));
        item.setUserId(resultSet.getInt(UnzipQueueItem.USER_ID));
        item.setProgressMessageId(resultSet.getInt(UnzipQueueItem.PROGRESS_MESSAGE_ID));
        UnzipQueueItem.ItemType itemType = UnzipQueueItem.ItemType.fromCode(resultSet.getInt(UnzipQueueItem.ITEM_TYPE));
        item.setItemType(itemType);
        if (columns.contains(QueueItem.QUEUE_POSITION)) {
            item.setQueuePosition(resultSet.getInt(QueueItem.QUEUE_POSITION));
        }

        if (itemType == UnzipQueueItem.ItemType.UNZIP) {
            TgFile tgFile = new TgFile();
            tgFile.setFileId(resultSet.getString(TgFile.FILE_ID));
            tgFile.setSize(resultSet.getInt(TgFile.SIZE));
            tgFile.setFormat(Format.valueOf(resultSet.getString(TgFile.FORMAT)));
            item.setFile(tgFile);

            item.setType(Format.valueOf(resultSet.getString(UnzipQueueItem.TYPE)));
        } else if (itemType == UnzipQueueItem.ItemType.EXTRACT_FILE) {
            item.setExtractFileId(resultSet.getInt(UnzipQueueItem.EXTRACT_FILE_ID));
            item.setExtractFileSize(resultSet.getLong(UnzipQueueItem.EXTRACT_FILE_SIZE));
        } else {
            item.setExtractFileSize(resultSet.getLong(UnzipQueueItem.EXTRACT_FILE_SIZE));
        }
        if (columns.contains(UnzipQueueItem.DOWNLOADS)) {
            PGobject downloadsArr = (PGobject) resultSet.getObject(UnzipQueueItem.DOWNLOADS);
            if (downloadsArr != null) {
                try {
                    List<Map<String, Object>> values = objectMapper.readValue(downloadsArr.getValue(), new TypeReference<>() {
                    });
                    List<DownloadQueueItem> downloadingQueueItems = new ArrayList<>();
                    for (Map<String, Object> value : values) {
                        DownloadQueueItem downloadingQueueItem = new DownloadQueueItem();
                        downloadingQueueItem.setFilePath((String) value.get(DownloadQueueItem.FILE_PATH));
                        downloadingQueueItem.setFile(objectMapper.convertValue(value.get(DownloadQueueItem.FILE), TgFile.class));
                        downloadingQueueItem.setDeleteParentDir((Boolean) value.get(DownloadQueueItem.DELETE_PARENT_DIR));
                        downloadingQueueItems.add(downloadingQueueItem);
                    }
                    item.setDownload(downloadingQueueItems.isEmpty() ? null : downloadingQueueItems.get(0));
                } catch (JsonProcessingException e) {
                    throw new SQLException(e);
                }
            }
        }

        return item;
    }
}
