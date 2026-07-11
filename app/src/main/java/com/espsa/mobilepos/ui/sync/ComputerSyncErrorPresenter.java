package com.espsa.mobilepos.ui.sync;

import com.espsa.mobilepos.app.sync.ComputerSyncException;
import com.espsa.mobilepos.app.sync.ComputerSyncFailureReason;
import com.espsa.mobilepos.ui.AppLanguage;
import com.espsa.mobilepos.ui.UiText;

public final class ComputerSyncErrorPresenter {
    private ComputerSyncErrorPresenter() {
    }

    public static ComputerSyncErrorPresentation presentError(
            ComputerSyncException exception,
            AppLanguage language
    ) {
        ComputerSyncFailureReason reason = exception == null
                ? ComputerSyncFailureReason.UNKNOWN
                : exception.reason();
        switch (reason) {
            case CONNECTION_TIMEOUT:
                return presentation(
                        language,
                        reason,
                        "无法到达电脑",
                        "No se puede acceder a la PC",
                        "连接电脑同步工具超时。",
                        "Se agoto el tiempo de conexion con la herramienta de PC.",
                        "请确认手机和电脑在同一 Wi-Fi，并检查 Windows 防火墙和路由器 AP 隔离。",
                        "Confirma que ambos equipos usen la misma Wi-Fi y revisa el firewall de Windows y el aislamiento AP."
                );
            case CONNECTION_REFUSED:
                return presentation(
                        language,
                        reason,
                        "电脑拒绝连接",
                        "La PC rechazo la conexion",
                        "电脑同步服务未接受连接。",
                        "El servicio de sincronizacion de PC no acepto la conexion.",
                        "请检查电脑同步工具、HTTP 服务是否已启动，以及端口是否正确。",
                        "Revisa la herramienta de PC, el servicio HTTP y el puerto."
                );
            case INVALID_TOKEN:
                return presentation(
                        language,
                        reason,
                        "Token 不正确",
                        "Token incorrecto",
                        "电脑端拒绝了当前访问令牌。",
                        "La PC rechazo el token actual.",
                        "请重新输入电脑工具显示的 Token。",
                        "Vuelve a ingresar el Token mostrado por la herramienta de PC."
                );
            case UNKNOWN_HOST:
                return presentation(
                        language,
                        reason,
                        "电脑 IP 无效",
                        "IP de PC no valida",
                        "无法解析或访问该电脑地址。",
                        "No se puede resolver ni acceder a esa direccion de PC.",
                        "请检查电脑工具显示的局域网 IPv4 地址。",
                        "Revisa la direccion IPv4 local mostrada por la herramienta de PC."
                );
            case INVALID_RESPONSE:
                return presentation(
                        language,
                        reason,
                        "不是同步服务",
                        "No es el servicio de sincronizacion",
                        "该地址返回的不是有效的 MobilePosSync 服务响应。",
                        "La direccion no devolvio una respuesta valida de MobilePosSync.",
                        "请确认电脑 IP 和端口对应的是电脑同步工具。",
                        "Confirma que la IP y el puerto pertenecen a la herramienta de PC."
                );
            case CLEAR_TEXT_BLOCKED:
                return presentation(
                        language,
                        reason,
                        "应用网络配置异常",
                        "Configuracion de red de la app",
                        "当前 APK 未允许局域网 HTTP 连接。",
                        "El APK actual no permite conexiones HTTP de red local.",
                        "请安装包含连接修复的最新 APK。",
                        "Instala el APK mas reciente con la correccion de conexion."
                );
            case INVALID_CONFIG:
                return presentation(
                        language,
                        reason,
                        "连接信息无效",
                        "Datos de conexion no validos",
                        "IP、端口或 Token 未通过校验。",
                        "La IP, el puerto o el Token no superaron la validacion.",
                        "请修正电脑 IP、端口或 Token 后重试。",
                        "Corrige la IP, el puerto o el Token y vuelve a intentarlo."
                );
            case HTTP_ERROR:
                return presentation(
                        language,
                        reason,
                        "电脑同步服务错误",
                        "Error del servicio de PC",
                        "电脑同步工具返回了未预期的 HTTP 状态。",
                        "La herramienta de PC devolvio un estado HTTP inesperado.",
                        "请检查电脑同步工具状态后重试。",
                        "Revisa el estado de la herramienta de PC e intentalo de nuevo."
                );
            default:
                return presentation(
                        language,
                        reason,
                        "电脑同步失败",
                        "Error de sincronizacion con PC",
                        "无法完成与电脑同步工具的连接。",
                        "No se pudo completar la conexion con la herramienta de PC.",
                        "请检查连接信息和电脑同步工具状态后重试。",
                        "Revisa los datos de conexion y la herramienta de PC antes de reintentar."
                );
        }
    }

    private static ComputerSyncErrorPresentation presentation(
            AppLanguage language,
            ComputerSyncFailureReason reason,
            String titleZh,
            String titleEs,
            String messageZh,
            String messageEs,
            String suggestionZh,
            String suggestionEs
    ) {
        return new ComputerSyncErrorPresentation(
                UiText.choose(language, titleZh, titleEs),
                UiText.choose(language, messageZh, messageEs),
                UiText.choose(language, suggestionZh, suggestionEs),
                reason
        );
    }
}
