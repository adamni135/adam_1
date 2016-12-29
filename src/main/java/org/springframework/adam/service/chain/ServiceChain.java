/**
 * 
 */
package org.springframework.adam.service.chain;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.adam.client.sevendays.ILogService;
import org.springframework.adam.common.bean.ResultVo;
import org.springframework.adam.common.bean.ThreadHolder;
import org.springframework.adam.common.bean.annotation.service.ServiceAsyn;
import org.springframework.adam.common.bean.annotation.service.ServiceErrorCode;
import org.springframework.adam.common.bean.annotation.service.ServiceFailRetryTimes;
import org.springframework.adam.common.bean.annotation.service.ServiceOrder;
import org.springframework.adam.common.bean.annotation.service.ServiceType;
import org.springframework.adam.common.bean.contants.AdamSysConstants;
import org.springframework.adam.common.bean.contants.BaseReslutCodeConstants;
import org.springframework.adam.common.utils.AdamClassUtils;
import org.springframework.adam.common.utils.AdamExceptionUtils;
import org.springframework.adam.common.utils.ThreadLocalHolder;
import org.springframework.adam.common.utils.TransactionUtil;
import org.springframework.adam.common.utils.context.SpringContextUtils;
import org.springframework.adam.service.IService;
import org.springframework.adam.service.common.AsynExcutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * @author user
 *
 */
@Component
@ServiceErrorCode(BaseReslutCodeConstants.CODE_SYSTEM_ERROR)
public class ServiceChain {

	private static final Log log = LogFactory.getLog(ServiceChain.class);
	/**
	 * servicesMap
	 */
	private Map<String, List<IService>> servicesMap = new HashMap<String, List<IService>>();

	private Map<String, Boolean> serviceAsynMap = new ConcurrentHashMap<String, Boolean>();

	@Autowired
	private ILogService logService;

	private AtomicBoolean isReady = new AtomicBoolean(false);

	public void init() {
		if (servicesMap == null || servicesMap.size() == 0) {
			initServiceChain();
		}
		checkReady();
	}

	/**
	 * 初始化
	 */
	private synchronized void initServiceChain() {
		// 判断过滤器表中是否有对象
		if (servicesMap != null && servicesMap.size() > 0) {
			return;
		}
		isReady.set(false);

		String[] serviceNames = SpringContextUtils.getSpringBeanNamesByType(IService.class);

		for (String name : serviceNames) {
			IService service = SpringContextUtils.getBean(name);

			Class clazz = AdamClassUtils.getTargetClass(service);

			ServiceType serviceType = (ServiceType) clazz.getAnnotation(ServiceType.class);
			ServiceOrder serviceOrder = (ServiceOrder) clazz.getAnnotation(ServiceOrder.class);

			String serviceTypeValue = "";
			int serviceOrderValue = 0;

			if (null == serviceType) {
				continue;
			} else {
				serviceTypeValue = serviceType.value();
			}

			if (null == serviceOrder) {
				serviceOrderValue = 0;
			} else {
				serviceOrderValue = serviceOrder.value();
			}
			putServiceInServicesMap(serviceTypeValue, serviceOrderValue, service);
		}

		isReady.set(true);
		log.info(this);
	}

	/**
	 * 把服务按顺序放进服务链
	 * 
	 * @param serviceEnum
	 * @param serviceOrderValue
	 * @param serivce
	 */
	private void putServiceInServicesMap(String serviceEnum, int serviceOrderValue, IService serivce) {
		List<IService> serviceList = servicesMap.get(serviceEnum);
		if (CollectionUtils.isEmpty(serviceList)) {
			serviceList = new ArrayList<IService>();
			servicesMap.put(serviceEnum, serviceList);
		}
		if (serviceList.size() == 0) {
			serviceList.add(serivce);
			return;
		}
		int realIndex = 0;
		for (int index = 0; index < serviceList.size(); index++) {
			IService serviceTmp = serviceList.get(index);
			ServiceOrder serviceOrderTmp = (ServiceOrder) AdamClassUtils.getTargetClass(serviceTmp).getAnnotation(ServiceOrder.class);
			if (null == serviceOrderTmp) {
				realIndex = 0;
				break;
			}
			if (serviceOrderValue <= serviceOrderTmp.value()) {
				realIndex = index;
				break;
			} else {
				realIndex++;
			}
		}
		serviceList.add(realIndex, serivce);
	}

	/**
	 * 处理服务
	 * 
	 * @param income
	 * @param output
	 * @param serviceEnum
	 * @throws Exception
	 */
	public void doServer(Object income, ResultVo output, String serviceEnum) {
		init();
		// 遍历过滤器
		List<IService> doneServiceStack = new ArrayList<IService>();
		boolean isSuccess = true;
		List<IService> serviceList = servicesMap.get(serviceEnum);
		if (CollectionUtils.isEmpty(serviceList)) {
			String msg = serviceEnum + "未能找到服务类别";
			log.error(msg);
			output.setResultCode(this.getClass(), BaseReslutCodeConstants.CODE_900001);
			output.setResultMsg(msg);
			return;
		}

		for (IService service : serviceList) {
			doneServiceStack.add(service);
			Class serviceClass = AdamClassUtils.getTargetClass(service);
			output.setLatestServiceName(serviceClass.getName());
			dealService(service, serviceClass, income, output);
			if (!BaseReslutCodeConstants.CODE_SUCCESS.equals(output.getResultCode())) { // success是成功且继续下一步
				if (!BaseReslutCodeConstants.CODE_SUCCESS_AND_BREAK.equals(output.getResultCode())) { // successAndBreak是成功且但不继续往下走
					isSuccess = false; // 其它情况均为错误
				}
				if (output.getResultCode().startsWith(BaseReslutCodeConstants.CODE_ERROR_BUT_CONTINUE)) { // errorButContinue是错误但是继续往下执行
					continue;
				}
				break;
			}
		}

		for (int index = 0; index < doneServiceStack.size(); index++) {
			IService service = doneServiceStack.get(doneServiceStack.size() - (index + 1));
			Class serviceClass = AdamClassUtils.getTargetClass(service);
			try {
				if (isSuccess) {
					dealSuccess(service, serviceClass, income, output);
				} else {
					dealFail(service, serviceClass, income, output);
				}
			} finally {
				dealComplate(service, serviceClass, income, output);
			}
		}

		if (!isSuccess) {
			TransactionUtil.transactionRollBack();
		}
	}

	/**
	 * 查处理链是否已经准备好
	 */
	private void checkReady() {
		for (int i = 0; i < 20; i++) {
			if (isReady.get()) {
				return;
			} else {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					log.error(e, e);
				}
			}
		}
	}

	/**
	 * 处理完成信息
	 * 
	 * @param doneServiceStack
	 * @param income
	 * @param output
	 * @param retryTime
	 */
	private void dealComplate(IService service, Class serviceClass, Object income, ResultVo output) {

		ServiceFailRetryTimes failRetryTimes = (ServiceFailRetryTimes) serviceClass.getAnnotation(ServiceFailRetryTimes.class);
		int retryTime = 1;
		if (null != failRetryTimes) {
			retryTime = failRetryTimes.complate();
		}

		int sleep = 0;
		if (null != failRetryTimes) {
			sleep = failRetryTimes.sleep();
		}

		String methodName = "doComplate";
		boolean isAsyn = getIsAsyn(service, serviceClass, methodName);
		if (isAsyn) {
			excASyn(service, serviceClass, income, output, methodName, retryTime, sleep, false);
		} else {
			excSyn(service, serviceClass, income, output, methodName, retryTime, sleep, false);
		}
	}

	/**
	 * 处理失败
	 * 
	 * @param service
	 * @param income
	 * @param output
	 * @param retryTime
	 */
	private void dealFail(IService service, Class serviceClass, Object income, ResultVo output) {

		ServiceFailRetryTimes failRetryTimes = (ServiceFailRetryTimes) serviceClass.getAnnotation(ServiceFailRetryTimes.class);
		int retryTime = 1;
		if (null != failRetryTimes) {
			retryTime = failRetryTimes.fail();
		}

		int sleep = 0;
		if (null != failRetryTimes) {
			sleep = failRetryTimes.sleep();
		}

		String methodName = "doFail";
		boolean isAsyn = getIsAsyn(service, serviceClass, methodName);
		if (isAsyn) {
			excASyn(service, serviceClass, income, output, methodName, retryTime, sleep, false);
		} else {
			excSyn(service, serviceClass, income, output, methodName, retryTime, sleep, false);
		}
	}

	/**
	 * 处理成功信息
	 * 
	 * @param service
	 * @param income
	 * @param output
	 * @param retryTime
	 */
	private void dealSuccess(IService service, Class serviceClass, Object income, ResultVo output) {

		ServiceFailRetryTimes failRetryTimes = (ServiceFailRetryTimes) serviceClass.getAnnotation(ServiceFailRetryTimes.class);
		int retryTime = 1;
		if (null != failRetryTimes) {
			retryTime = failRetryTimes.success();
		}

		int sleep = 0;
		if (null != failRetryTimes) {
			sleep = failRetryTimes.sleep();
		}

		String methodName = "doSuccess";
		boolean isAsyn = getIsAsyn(service, serviceClass, methodName);
		if (isAsyn) {
			excASyn(service, serviceClass, income, output, methodName, retryTime, sleep, false);
		} else {
			excSyn(service, serviceClass, income, output, methodName, retryTime, sleep, false);
		}
	}

	/**
	 * 进行服务
	 * 
	 * @param service
	 * @param income
	 * @param output
	 * @param serviceClass
	 */
	private void dealService(IService service, Class serviceClass, Object income, ResultVo output) {
		ServiceFailRetryTimes failRetryTimes = (ServiceFailRetryTimes) serviceClass.getAnnotation(ServiceFailRetryTimes.class);
		int retryTime = 1;
		if (null != failRetryTimes) {
			retryTime = failRetryTimes.server();
		}

		int sleep = 0;
		if (null != failRetryTimes) {
			sleep = failRetryTimes.sleep();
		}

		String methodName = "doService";
		boolean isAsyn = getIsAsyn(service, serviceClass, methodName);
		if (isAsyn) {
			excASyn(service, serviceClass, income, output, methodName, retryTime, sleep, true);
		} else {
			excSyn(service, serviceClass, income, output, methodName, retryTime, sleep, true);
		}
	}

	private void excASyn(IService service, Class serviceClass, Object income, ResultVo output, String methodName, int retryTime, int sleep, boolean isSetResultCode) {
		ThreadHolder threadHolder = ThreadLocalHolder.getThreadHolder();
		try {
			AsynExcutor asynExcutor = new AsynExcutor(service, serviceClass, income, output, methodName, retryTime, sleep, threadHolder);
			Thread thread = new Thread(asynExcutor);
			ThreadPoolTaskExecutor poolTaskExecutor = SpringContextUtils.getBean(ThreadPoolTaskExecutor.class, "taskExecutor");
			if (null == poolTaskExecutor) {
				thread.start();
			} else {
				poolTaskExecutor.execute(thread);
			}
		} catch (Exception e) {
			log.error(e, e);
			if (isSetResultCode) {
				if (output.success()) {
					output.setResultCode(this.getClass(), BaseReslutCodeConstants.CODE_900000);
				}
			}
			output.setResultMsg("system error occor:" + AdamExceptionUtils.getStackTrace(e));
		}
	}

	private void excSyn(IService service, Class serviceClass, Object income, ResultVo output, String methodName, int retryTime, int sleep, boolean isSetResultCode) {
		String oldResultCode = output.getResultCode();
		for (int retryTimeindex = 0; retryTimeindex < retryTime; retryTimeindex++) {
			long begin = System.currentTimeMillis();
			addBeginLog(service, serviceClass, income, output, methodName);
			try {
				if ("doService".equals(methodName)) {
					service.doService(income, output);
				} else if ("doSuccess".equals(methodName)) {
					service.doSuccess(income, output);
				} else if ("doFail".equals(methodName)) {
					service.doFail(income, output);
				} else if ("doComplate".equals(methodName)) {
					service.doComplate(income, output);
				} else {
					throw new Exception("没有对应方法名");
				}
				addEndLog(service, serviceClass, income, output, methodName, begin);
				break;
			} catch (Exception e) {
				log.error(e, e);
				if (isSetResultCode) {
					if (output.success()) {
						output.setResultCode(this.getClass(), BaseReslutCodeConstants.CODE_900000);
					}
				}
				output.setResultMsg("system error occor:" + AdamExceptionUtils.getStackTrace(e));
				// 不能放finally，要不然resultCode就不是真实的
				addEndLog(service, serviceClass, income, output, methodName, begin);
				if (retryTimeindex < retryTime - 1) {
					output.setResultCode(this.getClass(), oldResultCode);
				}
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					log.error(e, e);
				}
			}
		}

	}

	private boolean getIsAsyn(IService service, Class serviceClass, String methodName) {
		String key = serviceClass.getName() + methodName;
		boolean isAsyn = false;
		Boolean cacheIsAsyn = serviceAsynMap.get(key);
		if (null == cacheIsAsyn) {
			Class<?>[] paramClass = new Class<?>[2];
			paramClass[0] = Object.class;
			paramClass[1] = ResultVo.class;

			try {
				Method[] methods = serviceClass.getDeclaredMethods();
				ServiceAsyn serviceAsyn = null;
				for (Method thisMethod : methods) {
					if (methodName.equals(thisMethod.getName())) {
						serviceAsyn = thisMethod.getAnnotation(ServiceAsyn.class);
						if (null != serviceAsyn) {
							break;
						}
					}
				}

				if (null != serviceAsyn && serviceAsyn.value) {
					isAsyn = true;
				}
			} catch (Exception e1) {
				log.error(e1, e1);
			}
			serviceAsynMap.put(key, isAsyn);
		} else {
			isAsyn = cacheIsAsyn.booleanValue();
		}
		return isAsyn;
	}

	/**
	 * 重刷
	 */
	public void reset() {
		servicesMap = new ConcurrentHashMap<String, List<IService>>();
		initServiceChain();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ServiceChain [");
		sb.append(AdamSysConstants.LINE_SEPARATOR);
		int lineLong = 80;
		int orderLong = 6;
		for (Entry<String, List<IService>> entry : servicesMap.entrySet()) {
			sb.append(" MAP :" + entry.getKey());
			sb.append(AdamSysConstants.LINE_SEPARATOR);
			List<IService> serviceList = entry.getValue();
			for (IService service : serviceList) {
				String serverLine = "    ";
				Class serviceClass = AdamClassUtils.getTargetClass(service);
				ServiceOrder serviceOrder = (ServiceOrder) serviceClass.getAnnotation(ServiceOrder.class);
				if (null != serviceOrder) {
					String orderStr = serverLine + serviceOrder.value();
					if (orderStr.length() < orderLong) {
						for (int spaceIndex = 0; spaceIndex < (orderLong - orderStr.length()); spaceIndex++) {
							orderStr = orderStr + " ";
						}
					}
					serverLine = serverLine + orderStr + "  ";
				}
				serverLine = serverLine + serviceClass.getSimpleName();
				sb.append(serverLine);
				if (serverLine.length() < lineLong) {
					for (int spaceIndex = 0; spaceIndex < (lineLong - serverLine.length()); spaceIndex++) {
						sb.append(" ");
					}
				}
				sb.append("(" + serviceClass.getName() + ")");
				sb.append(AdamSysConstants.LINE_SEPARATOR);
			}
		}
		sb.append("]");
		return sb.toString();
	}

	/**
	 * @param service
	 * @param income
	 * @param output
	 * @param methodName
	 * @param remark
	 */
	private void addBeginLog(IService service, Class serviceClass, Object income, ResultVo output, String methodName) {
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
		if (!logService.isNeedLog()) {
			return;
		}

		ServiceFailRetryTimes failRetryTimes = (ServiceFailRetryTimes) serviceClass.getAnnotation(ServiceFailRetryTimes.class);
		if (null != failRetryTimes && !failRetryTimes.log()) {
			return;
		}
		methodName = AdamClassUtils.getTargetClass(service).getSimpleName() + "." + methodName;
		logService.sendRunningAccountLog(income, output, methodName, remark, beginTime);
	}

}
