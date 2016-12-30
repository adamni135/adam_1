# adam_1

1.某国内大型航空公司收益系统处理链引擎

2.某国内首位连锁酒店订单引擎处理链引擎

3.某国内首位特卖电商广告系统处理链引擎

A>分布式事务使用基于Base理论，支持TCC、Best Efforts 1PC模式，可以使用doService、doSuccess、doFail、doComplete实现大并发场景下事务处理

B>基于专家领域模型思想（DDD\CQRS）及RXjava思想的一种实现

C>日志打印收集基于ThreadLocal能在大并发场景下支持精准打印，分场景打印，有效解决大并发场景下日志的生成问题

D>各种小工具，如快速UUID生成，URLEncode等
