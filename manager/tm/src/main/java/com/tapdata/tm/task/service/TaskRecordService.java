package com.tapdata.tm.task.service;

import com.tapdata.tm.base.dto.Page;
import com.tapdata.tm.task.bean.SyncTaskStatusDto;
import com.tapdata.tm.task.entity.TaskRecord;
import com.tapdata.tm.task.vo.TaskRecordListVo;

public interface TaskRecordService {
    void createRecord(TaskRecord taskRecord);

    void updateTaskStatus(SyncTaskStatusDto dto);

    Page<TaskRecordListVo> queryRecords(String taskId, String offset, Integer limit);
}