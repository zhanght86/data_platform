package com.wlwx.service;

import com.wlwx.azkaban.AzkabanUtil;
import com.wlwx.back.task.ControlTask;
import com.wlwx.back.task.ExportTask;
import com.wlwx.back.util.PlatformUtil;
import com.wlwx.back.util.ResultMsg;
import com.wlwx.back.util.UuidUtil;
import com.wlwx.dao.AzUserInfoMapper;
import com.wlwx.dao.ModelInfoMapper;
import com.wlwx.dao.TaskInfoMapper;
import com.wlwx.model.AzUserInfo;
import com.wlwx.model.ModelInfo;
import com.wlwx.model.TaskInfo;
import com.wlwx.model.UserInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("smsExportService")
public class SmsExportService {
	private static final Logger LOGGER = Logger
			.getLogger(SmsExportService.class);

	@Autowired
	private TaskService taskService;
	
	@Autowired
	private TaskInfoMapper taskInfoMapper;
	
	@Autowired
	private AzUserInfoMapper azUserInfoMapper;

	@Autowired
	private ModelInfoMapper modelInfoMapper;

	public ResultMsg createTask(Map<String, Object> map) {
		ResultMsg resultMsg = null;
		try {
			UserInfo userInfo = (UserInfo) map.get("user_info");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
			map.remove("user_info");
			map.remove("custom_info");
			if (userInfo != null) {
				TaskInfo taskInfo = new TaskInfo();

				String taskId = UuidUtil.getUUID();
				Date createTime = new Date();

				String modelId = map.get("model_id").toString();
				map.remove("model_id");
				
				for (String key : map.keySet()) {
					if (key.indexOf("time") > 0) { //特殊处理 如果是时间类型，需要添加单引号
						map.put(key, "'" + map.get(key).toString() + "'");
					}
				}

				String taskParam = PlatformUtil.mapToJson(map);
				map.put("user_id", userInfo.getUser_id());
				map.put("user_source", userInfo.getCustom_source());
				map.put("create_time", sdf.format(createTime));
				map.put("useExecutor", AzkabanUtil.HADOOP03);
				map.put("task_id", taskId);

				ExportTask exportTask = new ExportTask();
				exportTask.setTask_id(taskId);
				exportTask.setUser_id(userInfo.getUser_id());
				exportTask.setCreate_time(createTime);
				exportTask.setPriority(userInfo.getUser_priority());
				exportTask.setModel_id(modelId);
				exportTask.setState(TaskInfo.NEW);
				exportTask.setTask_param(map);
				exportTask.setTaskService(taskService);

				AzUserInfo azUserInfo = azUserInfoMapper.get(userInfo
						.getAz_user_id());
				if (azUserInfo != null) {
					exportTask.setAzUserInfo(azUserInfo);
					ModelInfo modelInfo = modelInfoMapper.get(modelId);

					if (modelInfo != null) {
						exportTask.setModelInfo(modelInfo);
						resultMsg = ControlTask.startTask(exportTask);
						if (resultMsg.isSuccess()) {
							taskInfo.setTask_id(taskId);
							taskInfo.setUser_id(userInfo.getUser_id());
							taskInfo.setCreate_time(createTime);
							taskInfo.setPriority(userInfo.getUser_priority());
							taskInfo.setModel_id(modelId);
							taskInfo.setTask_status(1);
							taskInfo.setTask_param(taskParam);

							taskInfoMapper.insertTask(taskInfo);
							resultMsg = new ResultMsg(true , "创建任务成功", taskId);
						} else {
							resultMsg = new ResultMsg(false, resultMsg.getMsg());
						}
					} else {
						resultMsg = new ResultMsg(false, "模板信息不存在");
					}
				} else {
					resultMsg = new ResultMsg(false, "阿兹卡班用户信息不存在");
				}
			} else {
				resultMsg = new ResultMsg(false, "客户端用户信息错误");
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			resultMsg = new ResultMsg(false, "添加任务失败");
		}

		return resultMsg;
	}
}