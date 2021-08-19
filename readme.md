# 编译加速应用插件

# 打包

插件需要打包成zip包形式,具体步骤如下


1. cd src/backend/core
2. gradle clean buildZip
3. 用src\backend\core\build\distributions\TurboAtom.zip上传

# 配置

1) 新建插件
   内容如图填写
   插件调试项目按用户实际定义，一般是常用的调试项目



2）需要为配置项赋值：
BK_TURBO_PUBLIC_URL

3）上架插件


需要填写的内容：  
插件名称：编译加速插件  
简介：  Turbo编译加速基于分布式编译、缓存、容器技术，旨在为用户提供高效、稳定、便捷的加速服务，提升研发效率。  
详细描述：  Turbo编译加速基于分布式编译、缓存、容器技术，旨在为用户提供高效、稳定、便捷的加速服务，提升研发效率。  
发布者：Turbo  

其他信息：

logo：



4）打包ok就点继续


注意：
后续升级插件，尽量不要用不兼容性升级，要选截图这个


