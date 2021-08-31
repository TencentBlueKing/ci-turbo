# 编译加速插件

# 打包

插件需要打包成zip包形式,具体步骤如下


1. cd src/backend/core
2. gradle clean buildZip
3. 用src\backend\core\build\distributions\Turbo.zip上传

# 配置

1) 新建插件
   内容如图填写
   插件调试项目按用户实际定义，一般是常用的调试项目
![image](https://user-images.githubusercontent.com/21979373/130041119-efe9422c-e72d-4871-8693-a0807cb6c2cb.png)




2）需要为配置项赋值：
BK_TURBO_PUBLIC_URL
![image](https://user-images.githubusercontent.com/21979373/130024293-0b9228f3-8210-44f0-b58d-180f9afe6fd4.png)


3）上架插件
![image](https://user-images.githubusercontent.com/21979373/130024593-8ac2ceb2-fffc-4ebf-9d2f-35dbe385486d.png)


需要填写的内容：  
插件名称：编译加速插件  
简介：  Turbo编译加速基于分布式编译、缓存、容器技术，旨在为用户提供高效、稳定、便捷的加速服务，提升研发效率。  
详细描述：  Turbo编译加速基于分布式编译、缓存、容器技术，旨在为用户提供高效、稳定、便捷的加速服务，提升研发效率。  
发布者：Turbo  

其他信息：  
![image](https://user-images.githubusercontent.com/21979373/130024909-0f4c4e3e-1be7-4651-aef3-f768582c635b.png)

logo：  
[图片]![image](https://user-images.githubusercontent.com/21979373/131462245-e03ffb04-0d9f-48c0-ab23-ded840b38597.png)




4）打包ok就点继续



