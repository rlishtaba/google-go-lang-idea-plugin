package ro.redeul.google.go.config.sdk;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * Author: Toader Mihai Claudiu <mtoader@gmail.com>
 * <p/>
 * Date: 8/10/11
 * Time: 8:14 AM
 */
public class GoAppEngineSdkData implements SdkAdditionalData, PersistentStateComponent<GoAppEngineSdkData> {

    public String GO_HOME_PATH = "";

    public GoTargetOs TARGET_OS = null;
    public GoTargetArch TARGET_ARCH = null;

    public String VERSION_MAJOR = "";
    public String VERSION_MINOR = "";

    public GoAppEngineSdkData() {
    }

    public GoAppEngineSdkData(String homePath, String binPath, GoTargetOs TARGET_OS, GoTargetArch TARGET_ARCH, String VERSION_MAJOR, String VERSION_MINOR) {
        this.GO_HOME_PATH = homePath;
        this.TARGET_OS = TARGET_OS;
        this.TARGET_ARCH = TARGET_ARCH;
        this.VERSION_MAJOR = VERSION_MAJOR;
        this.VERSION_MINOR = VERSION_MINOR;
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public void checkValid(SdkModel sdkModel) throws ConfigurationException {
        //
    }

    @Override
    public void loadState(GoAppEngineSdkData state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public GoAppEngineSdkData getState() {
        return this;
    }

}
