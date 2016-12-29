/**
 * 
 */
package org.springframework.adam.common.aop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.adam.client.sevendays.ILogService;
import org.springframework.adam.common.bean.RequestLogEntity;
import org.springframework.adam.common.utils.AdamUUIDUtils;
import org.springframework.adam.common.utils.TimeoutUtil;
import org.springframework.adam.common.utils.context.SpringContextUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;

/**
 * @author user
 *
 */
@Component
@Aspect
@Order(0)
public class RpcClientAspect {
	private static final Log log = LogFactory.getLog(RpcClientAspect.class);

	@Around("@annotation(org.springframework.adam.common.bean.annotation.service.RpcClient)")
	public Object aroundMethod(ProceedingJoinPoint pjp) throws Throwable {
		return doinvoke(pjp);
	}

	private Object doinvoke(ProceedingJoinPoint pjp) throws Throwable {
		Object result = null;
		String logStr = "";
		Object[] args = pjp.getArgs();
		String reqStr = JSON.toJSONString(args);
		String processId = getNextProcessId();
		String header = "rpc client";
		Signature method = pjp.getSignature();
		long begin = System.currentTimeMillis();
		log.info(new StringBuilder(processId).append(" ").append(method.toString()).append(" input: ").append(reqStr).toString());
		ILogService logService = null;
		if (SpringContextUtils.isContextInjected()) {
			logService = SpringContextUtils.getBean(ILogService.class);
		}
		try {
			result = pjp.proceed();
			logStr = JSON.toJSONString(result);
			return result;
		} catch (Exception e) {
			logStr = e.getMessage();
			if (null != logService) {
				if (TimeoutUtil.isTimeOut(e)) {
					logService.sendOverTimeAccountLog(reqStr, logStr, method.toString(), "请求超时");
				} else {
					logService.sendTechnologyErrorAccountLog(reqStr, logStr, method.toString(), "系统异常");
				}
			}
			throw e;
		} finally {
			long end = System.currentTimeMillis();
			log.info(new StringBuilder(processId).append(" output: ").append(logStr).append(" used time: ").append(end - begin).toString());
			if (null != logService) {
				RequestLogEntity requestLogEntity = new RequestLogEntity();
				requestLogEntity.setUrl(method.toString());
				requestLogEntity.setHeader(header);
				requestLogEntity.setRequest(reqStr);
				requestLogEntity.setResponse(logStr);
				requestLogEntity.setUseTime(end - begin);
				logService.sendRequestLog(requestLogEntity);
			}
		}
	}

	private String getNextProcessId() {
		return AdamUUIDUtils.getUUID();
	}
}
