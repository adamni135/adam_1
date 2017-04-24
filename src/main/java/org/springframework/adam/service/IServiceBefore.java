package org.springframework.adam.service;

import org.springframework.adam.common.bean.ResultVo;

public interface IServiceBefore {

	/**
	 * doService 进行服务前做的事
	 * 
	 * @param income
	 *            入参
	 * @param output
	 *            结果
	 * @return 是否执行这个service
	 * 
	 * @throws Exception
	 *             Exception
	 */
	boolean dealServiceBefore(IService service, Class serviceClass, Object income, ResultVo output);

	/**
	 * doSuccess 成功服务前做的事
	 * 
	 * @param income
	 *            入参
	 * @param output
	 *            结果
	 * @return 是否执行这个service
	 * 
	 * @throws Exception
	 *             Exception
	 */
	boolean dealSuccessBefore(IService service, Class serviceClass, Object income, ResultVo output);

	/**
	 * doFail 失败服务前做的事
	 * 
	 * @param income
	 *            入参
	 * @param output
	 *            结果
	 * @return 是否执行这个service
	 * 
	 * @throws Exception
	 *             Exception
	 */
	boolean dealFailBefore(IService service, Class serviceClass, Object income, ResultVo output);

	/**
	 * doComplate 完成服务前做的事
	 * 
	 * @param income
	 *            入参
	 * @param output
	 *            结果
	 * @return 是否执行这个service
	 * 
	 * @throws Exception
	 *             Exception
	 */
	boolean dealComplateBefore(IService service, Class serviceClass, Object income, ResultVo output);
}