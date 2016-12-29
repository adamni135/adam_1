/**
 * 
 */
package org.springframework.adam.service.common;

import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.adam.client.sevendays.ILogService;
import org.springframework.adam.common.bean.ResultVo;
import org.springframework.adam.common.bean.ThreadHolder;
import org.springframework.adam.common.bean.annotation.service.ServiceErrorCode;
import org.springframework.adam.common.bean.annotation.service.ServiceFailRetryTimes;
import org.springframework.adam.common.bean.contants.BaseReslutCodeConstants;
import org.springframework.adam.common.utils.AdamClassUtils;
import org.springframework.adam.common.utils.ThreadLocalHolder;
import org.springframework.adam.common.utils.context.SpringContextUtils;
import org.springframework.adam.service.IService;
import org.springframework.beans.BeanUtils;

/**
 * @author user
 *
 */
@ServiceErrorCode(BaseReslutCodeConstants.CODE_SYSTEM_ERROR)
public class AsynExcutor implements Callable<ResultVo>, Runnable {

	private static final Log log = LogFactory.getLog(AsynExcutor.class);

	public AsynExcutor(IService service, Class serviceClass, Object param, ResultVo resultVo, String methodName, int retryTime, int sleep, ThreadHolder threadHolder) {
		super();
		this.service = service;
		this.serviceClass = serviceClass;
		setParam(param);
		setResultVo(resultVo);
		this.methodName = methodName;
		this.retryTime = retryTime;
		setThreadHolder(threadHolder);
	}

	private IService service;

	private Class serviceClass;

	private Object param;

	private ResultVo resultVo;

	private String methodName;

	private int retryTime;

	private int sleep;

	private ThreadHolder threadHolder;

	public ThreadHolder getThreadHolder() {
		return threadHolder;
	}

	public void setThreadHolder(ThreadHolder threadHolder) {
		ThreadHolder threadHolderClone = new ThreadHolder();
		BeanUtils.copyProperties(threadHolder, threadHolderClone);
		this.threadHolder = threadHolderClone;
	}

	public int getRetryTime() {
		return retryTime;
	}

	public void setRetryTime(int retryTime) {
		this.retryTime = retryTime;
	}

	public Object getParam() {
		return param;
	}

	public void setParam(Object param) {
		try {
			Class paramCloneClass = param.getClass();
			Object paramClone = paramCloneClass.newInstance();
			BeanUtils.copyProperties(param, paramClone);
			this.param = paramClone;
		} catch (Exception e) {
			log.error(e, e);
		}
	}

	public ResultVo getResultVo() {
		return resultVo;
	}

	public void setResultVo(ResultVo resultVo) {
		ResultVo resultVoClone = new ResultVo();
		BeanUtils.copyProperties(resultVo, resultVoClone);
		this.resultVo = resultVoClone;
	}

	public IService getService() {
		return service;
	}

	public void setService(IService service) {
		this.service = service;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	private ResultVo work() {
		ThreadLocalHolder.setThreadHolder(threadHolder);
		for (int retryTimeindex = 0; retryTimeindex < retryTime; retryTimeindex++) {
			String oldResultCode = resultVo.getResultCode();
			long begin = System.currentTimeMillis();
			addBeginLog(service, param, resultVo, methodName);
			try {
				if ("doService".equals(methodName)) {
					service.doService(param, resultVo);
				} else if ("doSuccess".equals(methodName)) {
					service.doSuccess(param, resultVo);
				} else if ("doFail".equals(methodName)) {
					service.doFail(param, resultVo);
				} else if ("doComplate".equals(methodName)) {
					service.doComplate(param, resultVo);
				} else {
					throw new Exception("没有对应方法名");
				}
				addLog(service, serviceClass, param, resultVo, methodName, "end", begin);
				break;
			} catch (Exception e) {
				log.error(e, e);
				if (resultVo.success()) {
					resultVo.setResultCode(this.getClass(), BaseReslutCodeConstants.CODE_900000);
				}
				resultVo.setResultMsg("system error occor" + e);
				addLog(service, serviceClass, param, resultVo, methodName, "end", begin);
				if (retryTimeindex < retryTime - 1) {
					resultVo.setResultCode(this.getClass(), oldResultCode);
				}
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					log.error(e, e);
				}
			}
		}
		return resultVo;
	}

	/**
	 * @param service
	 * @param income
	 * @param output
	 * @param methodName
	 * @param remark
	 */
	private void addBeginLog(IService service, Object income, ResultVo output, String methodName) {
		addLog(service, serviceClass, income, output, methodName, "begin", null);
	}

	/**
	 * 增加日志
	 * 
	 * @param service
	 * @param income
	 * @param output
	 * @param methodName
	 * @param remark
	 * @param begin
	 */
	private void addEndLog(IService service, Class serviceClass, Object income, ResultVo output, String methodName, Long beginTime) {
		addLog(service, serviceClass, income, output, methodName, "end", beginTime);
	}

	/**
	 * 增加日志
	 * 
	 * @param service
	 * @param income
	 * @param output
	 * @param methodName
	 * @param remark
	 * @param begin
	 */
	private void addLog(IService service, Class serviceClass, Object income, ResultVo output, String methodName, String remark, Long beginTime) {
		ILogService logService = SpringContextUtils.getBean(ILogService.class);
		if (!logService.isNeedLog()) {
			return;
		}
		ServiceFailRetryTimes failRetryTimes = (ServiceFailRetryTimes) serviceClass.getAnnotation(ServiceFailRetryTimes.class);
		if (null == failRetryTimes) {
			return;
		}
		if (!failRetryTimes.log()) {
			return;
		}
		methodName = AdamClassUtils.getTargetClass(service).getSimpleName() + "." + methodName;
		logService.sendRunningAccountLog(income, output, methodName, remark, beginTime);
	}

	@Override
	public void run() {
		work();
	}

	@Override
	public ResultVo call() throws Exception {
		return work();
	}

	public int getSleep() {
		return sleep;
	}

	public void setSleep(int sleep) {
		this.sleep = sleep;
	}
}
