/**
 * Copyright (c) 2017 m2049r
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <inttypes.h>
#include "monerujo.h"
#include "wallet2_api.h"

//TODO explicit casting jlong, jint, jboolean to avoid warnings

#ifdef __cplusplus
extern "C"
{
#endif

#include <android/log.h>
#define LOG_TAG "WalletNDK"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , LOG_TAG,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO   , LOG_TAG,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN   , LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG,__VA_ARGS__)

static JavaVM *cachedJVM;
static jclass class_WalletStatus;
static jclass class_BluetoothService;
static jclass class_SidekickService;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cachedJVM = jvm;
    LOGI("JNI_OnLoad");
    JNIEnv *jenv;
    if (jvm->GetEnv(reinterpret_cast<void **>(&jenv), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    class_WalletStatus = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("org/guntherkorp/sidekick/model/Wallet$Status")));
    class_BluetoothService = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("org/guntherkorp/sidekick/service/BluetoothService")));
    class_SidekickService = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("org/guntherkorp/sidekick/service/SidekickService")));
    return JNI_VERSION_1_6;
}
#ifdef __cplusplus
}
#endif

int attachJVM(JNIEnv **jenv) {
    int envStat = cachedJVM->GetEnv((void **) jenv, JNI_VERSION_1_6);
    if (envStat == JNI_EDETACHED) {
        if (cachedJVM->AttachCurrentThread(jenv, nullptr) != 0) {
            LOGE("Failed to attach");
            return JNI_ERR;
        }
    } else if (envStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return JNI_ERR;
    }
    return envStat;
}

void detachJVM(JNIEnv *jenv, int envStat) {
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
    }

    if (envStat == JNI_EDETACHED) {
        cachedJVM->DetachCurrentThread();
    }
}

#ifdef __cplusplus
extern "C"
{
#endif

/****************************************/
/********** from WalletManager **********/
/****************************************/
JNIEXPORT jlong JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_createWalletJ(JNIEnv *env, jclass clazz,
                                                         jstring path, jstring password,
                                                         jstring language,
                                                         jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWallet(
                    std::string(_path),
                    std::string(_password),
                    std::string(_language),
                    _networkType);

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_openWalletJ(JNIEnv *env, jclass clazz,
                                                          jstring path, jstring password,
                                                          jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->openWallet(
                    std::string(_path),
                    std::string(_password),
                    _networkType);

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_recoveryWalletJ(JNIEnv *env, jclass clazz,
                                                                  jstring path, jstring password,
                                                                  jstring mnemonic, jstring offset,
                                                                  jint networkType,
                                                                  jlong restoreHeight) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->recoveryWallet(
                    std::string(_path),
                    std::string(_password),
                    std::string(_mnemonic),
                    _networkType,
                    (uint64_t) restoreHeight,
                    1, // kdf_rounds
                    std::string(_offset));

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_createWalletFromKeysJ(JNIEnv *env, jclass clazz,
                                                                        jstring path,
                                                                        jstring password,
                                                                        jstring language,
                                                                        jint networkType,
                                                                        jlong restoreHeight,
                                                                        jstring addressString,
                                                                        jstring viewKeyString,
                                                                        jstring spendKeyString) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    const char *_addressString = env->GetStringUTFChars(addressString, nullptr);
    const char *_viewKeyString = env->GetStringUTFChars(viewKeyString, nullptr);
    const char *_spendKeyString = env->GetStringUTFChars(spendKeyString, nullptr);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromKeys(
                    std::string(_path),
                    std::string(_password),
                    std::string(_language),
                    _networkType,
                    (uint64_t) restoreHeight,
                    std::string(_addressString),
                    std::string(_viewKeyString),
                    std::string(_spendKeyString));

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    env->ReleaseStringUTFChars(addressString, _addressString);
    env->ReleaseStringUTFChars(viewKeyString, _viewKeyString);
    env->ReleaseStringUTFChars(spendKeyString, _spendKeyString);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jboolean JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_isAddressValid(JNIEnv *env, jclass clazz,
                                                      jstring address, jint networkType) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    bool isValid = Monero::Wallet::addressValid(_address, _networkType);
    env->ReleaseStringUTFChars(address, _address);
    return static_cast<jboolean>(isValid);
}

JNIEXPORT jboolean JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_walletExists(JNIEnv *env, jclass clazz,
                                                        jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    bool exists =
            Monero::WalletManagerFactory::getWalletManager()->walletExists(std::string(_path));
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(exists);
}

JNIEXPORT jint JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_queryWalletDeviceJ(JNIEnv *env, jclass clazz,
                                                              jstring keys_file_name,
                                                              jstring password) {
    const char *_keys_file_name = env->GetStringUTFChars(keys_file_name, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::Wallet::Device device_type;
    bool ok = Monero::WalletManagerFactory::getWalletManager()->
            queryWalletDevice(device_type, std::string(_keys_file_name), std::string(_password));
    env->ReleaseStringUTFChars(keys_file_name, _keys_file_name);
    env->ReleaseStringUTFChars(password, _password);
    if (ok)
        return static_cast<jint>(device_type);
    else
        return -1;
}

JNIEXPORT jboolean JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_close(JNIEnv *env, jclass clazz,
                                                        jobject walletInstance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, walletInstance);
    bool closeSuccess = Monero::WalletManagerFactory::getWalletManager()->closeWallet(wallet,
                                                                                      false);
    LOGD("wallet closed");
    return static_cast<jboolean>(closeSuccess);
}

/**********************************/
/************ Wallet **************/
/**********************************/

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getSeed(JNIEnv *env, jobject instance,
                                                   jstring seedOffset) {
    const char *_seedOffset = env->GetStringUTFChars(seedOffset, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    jstring seed = env->NewStringUTF(wallet->seed(std::string(_seedOffset)).c_str());
    env->ReleaseStringUTFChars(seedOffset, _seedOffset);
    return seed;
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getSeedLanguage(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->getSeedLanguage().c_str());
}

jobject newWalletStatusInstance(JNIEnv *env, int status, const std::string &errorString) {
    jmethodID init = env->GetMethodID(class_WalletStatus, "<init>",
                                      "(ILjava/lang/String;)V");
    jstring _errorString = env->NewStringUTF(errorString.c_str());
    jobject instance = env->NewObject(class_WalletStatus, init, status, _errorString);
    env->DeleteLocalRef(_errorString);
    return instance;
}

JNIEXPORT jobject JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_statusWithErrorString(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    int status;
    std::string errorString;
    wallet->statusWithErrorString(status, errorString);

    return newWalletStatusInstance(env, status, errorString);
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getPath(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->path().c_str());
}

JNIEXPORT jboolean JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_setPassword(JNIEnv *env, jobject instance,
                                                       jstring password) {
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->setPassword(std::string(_password));
    env->ReleaseStringUTFChars(password, _password);
    return static_cast<jboolean>(success);
}

JNIEXPORT jint JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_nettype(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->nettype();
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getSecretViewKey(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretViewKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getSecretSpendKey(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretSpendKey().c_str());
}

JNIEXPORT jboolean JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_store(JNIEnv *env, jobject instance,
                                                 jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->store(std::string(_path));
    if (!success) {
        LOGE("store() %s", wallet->errorString().c_str());
    }
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getFilename(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->filename().c_str());
}

JNIEXPORT jint JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getDeviceTypeJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::Wallet::Device device_type = wallet->getDeviceType();
    return static_cast<jint>(device_type);
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getAddress(JNIEnv *env, jobject instance,
                                                      jint accountIndex,
                                                      jint addressIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(
            wallet->address((uint32_t) accountIndex, (uint32_t) addressIndex).c_str());
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_setRestoreHeight(JNIEnv *env, jobject instance,
                                                            jlong height) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setRefreshFromBlockHeight((uint64_t) height);
}

JNIEXPORT jlong JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_getRestoreHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->getRefreshFromBlockHeight();
}

JNIEXPORT jboolean JNICALL
Java_org_guntherkorp_sidekick_model_Wallet_isWatchOnly(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->watchOnly());
}

JNIEXPORT jbyteArray JNICALL
Java_org_guntherkorp_sidekick_util_KeyStoreHelper_slowHash(JNIEnv *env, jclass clazz,
                                                           jbyteArray data, jint brokenVariant) {
    char hash[HASH_SIZE];
    jsize size = env->GetArrayLength(data);
    if ((brokenVariant > 0) && (size < 200 /*sizeof(union hash_state)*/)) {
        return nullptr;
    }

    jbyte *buffer = env->GetByteArrayElements(data, nullptr);
    switch (brokenVariant) {
        case 1:
            slow_hash_broken(buffer, hash, 1);
            break;
        case 2:
            slow_hash_broken(buffer, hash, 0);
            break;
        default: // not broken
            slow_hash(buffer, (size_t) size, hash);
    }
    env->ReleaseByteArrayElements(data, buffer, JNI_ABORT); // do not update java byte[]
    jbyteArray result = env->NewByteArray(HASH_SIZE);
    env->SetByteArrayRegion(result, 0, HASH_SIZE, (jbyte *) hash);
    return result;
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Logger_initLogger(JNIEnv *env, jclass clazz,
                                                             jstring argv0,
                                                             jstring default_log_base_name) {

    const char *_argv0 = env->GetStringUTFChars(argv0, nullptr);
    const char *_default_log_base_name = env->GetStringUTFChars(default_log_base_name, nullptr);

    Monero::Wallet::init(_argv0, _default_log_base_name);

    env->ReleaseStringUTFChars(argv0, _argv0);
    env->ReleaseStringUTFChars(default_log_base_name, _default_log_base_name);
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Logger_logDebug(JNIEnv *env, jclass clazz,
                                                       jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::debug(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Logger_logInfo(JNIEnv *env, jclass clazz,
                                                      jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::info(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Logger_logWarning(JNIEnv *env, jclass clazz,
                                                         jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::warning(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Logger_logError(JNIEnv *env, jclass clazz,
                                                       jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::error(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_Logger_setLogLevel(JNIEnv *env, jclass clazz,
                                                          jint level) {
    Monero::WalletManagerFactory::setLogLevel(level);
}

JNIEXPORT jstring JNICALL
Java_org_guntherkorp_sidekick_model_Logger_moneroVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(MONERO_VERSION);
}

//
// SidekickWallet Stuff
//

/**
 * @brief BtExchange     - exchange data with Monerujo Device
 * @param request        - buffer for data to send
 * @param request_len    - length of data to send
 * @param response       - buffer for received data
 * @param max_resp_len   - size of receive buffer
 *
 * @return length of received data in response or -1 if error, -2 if response buffer too small
 */
int BtExchange(
        unsigned char *request,
        unsigned int request_len,
        unsigned char *response,
        unsigned int max_resp_len) {
    JNIEnv *jenv;
    int envStat = attachJVM(&jenv);
    if (envStat == JNI_ERR) return -16;

    //TODO cache this?
    jmethodID exchangeMethod = jenv->GetStaticMethodID(class_BluetoothService, "Exchange",
                                                       "([B)[B");

    auto reqLen = static_cast<jsize>(request_len);
    jbyteArray reqData = jenv->NewByteArray(reqLen);
    jenv->SetByteArrayRegion(reqData, 0, reqLen, (jbyte *) request);
    LOGD("BtExchange cmd: 0x%02x with %u bytes", request[0], reqLen);
    auto dataRecv = (jbyteArray)
            jenv->CallStaticObjectMethod(class_BluetoothService, exchangeMethod, reqData);
    jenv->DeleteLocalRef(reqData);
    if (dataRecv == nullptr) {
        detachJVM(jenv, envStat);
        LOGD("BtExchange: error reading");
        return -1;
    }
    jsize respLen = jenv->GetArrayLength(dataRecv);
    LOGD("BtExchange response is %u bytes", respLen);
    if (respLen <= max_resp_len) {
        jenv->GetByteArrayRegion(dataRecv, 0, respLen, (jbyte *) response);
        jenv->DeleteLocalRef(dataRecv);
        detachJVM(jenv, envStat);
        return static_cast<int>(respLen);;
    } else {
        jenv->DeleteLocalRef(dataRecv);
        detachJVM(jenv, envStat);
        LOGE("BtExchange response buffer too small: %u < %u", respLen, max_resp_len);
        return -2;
    }
}

/**
 * @brief ConfirmTransfers
 * @param transfers - string of "fee (':' address ':' amount)+"
 *
 * @return true on accept, false on reject
 */
bool ConfirmTransfers(const char *transfers) {
    JNIEnv *jenv;
    int envStat = attachJVM(&jenv);
    if (envStat == JNI_ERR) return -16;

    jmethodID confirmMethod = jenv->GetStaticMethodID(class_SidekickService, "ConfirmTransfers",
                                                      "(Ljava/lang/String;)Z");

    jstring _transfers = jenv->NewStringUTF(transfers);
    auto confirmed =
            jenv->CallStaticBooleanMethod(class_SidekickService, confirmMethod, _transfers);
    jenv->DeleteLocalRef(_transfers);
    return confirmed;
}


// SidekickWallet

JNIEXPORT jlong JNICALL
Java_org_guntherkorp_sidekick_model_SidekickWallet_loadFromWalletJ(JNIEnv *env, jclass clazz,
                                                                   jstring path, jstring password,
                                                                   jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::WalletManager *mgr = Monero::WalletManagerFactory::getWalletManager();
    Monero::Wallet *wallet = mgr->openWallet(std::string(_path),
                                             std::string(_password),
                                             _networkType);

    Monerujo::SidekickWallet *sidekickWallet = nullptr;
    int status;
    std::string errorString;
    wallet->statusWithErrorString(status, errorString);
    if (status == Monero::Wallet::Status_Ok) {
        sidekickWallet = new Monerujo::SidekickWallet(static_cast<uint8_t>(networkType),
                                                      wallet->secretSpendKey(),
                                                      wallet->secretViewKey());
    }
    if (!mgr->closeWallet(wallet, false)) delete wallet;
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    if (sidekickWallet == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", errorString.c_str());
    }
    return reinterpret_cast<jlong>(sidekickWallet);
}

JNIEXPORT jbyteArray JNICALL
Java_org_guntherkorp_sidekick_model_SidekickWallet_call(JNIEnv *env, jobject instance,
                                                        jint id, jbyteArray request) {
    jsize size = env->GetArrayLength(request);
    jbyte *_request = env->GetByteArrayElements(request, nullptr);

    auto *wallet = getHandle<Monerujo::SidekickWallet>(env, instance);
    std::string _response = wallet->call(id, std::string((char *) _request, size));

    env->ReleaseByteArrayElements(request, _request, JNI_ABORT); // do not update java byte[]
    jbyteArray response = env->NewByteArray(_response.size());
    env->SetByteArrayRegion(response, 0, _response.size(), (jbyte *) _response.c_str());
    return response;
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_SidekickWallet_reset(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monerujo::SidekickWallet>(env, instance);
    wallet->reset();
}

JNIEXPORT void JNICALL
Java_org_guntherkorp_sidekick_model_SidekickWallet_destroy(JNIEnv *env, jobject instance,
                                                           jlong handle) {
    destroyNativeObject(env, handle, instance);
}

JNIEXPORT jint JNICALL
Java_org_guntherkorp_sidekick_model_SidekickWallet_getStatusJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monerujo::SidekickWallet>(env, instance);
    return (int) wallet->status();
}

#ifdef __cplusplus
}
#endif
