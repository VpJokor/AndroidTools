# ScreenShotUtils
### 系统截屏监听工具，监听系统截屏，然后对截图进行处理   

需要权限：Manifest.permission.READ_EXTERNAL_STORAG   
使用方法：  
在onCreate中注册   
    `if (PermissionUtils.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            ScreenShotUtils.getInstance().register(this) {
                //系统截屏
                val bottomShareToView = BottomShareToView(this, 2)
                bottomShareToView.sessionBean = model.sessionLiveData.value
                bottomShareToView.sharePath = it
                bottomShareToView.shareContent = 2
                DialogUtils.showBottomView(this, bottomShareToView)
            }
    }`
在onDestroy中注销  
ScreenShotUtils.getInstance().unregister()