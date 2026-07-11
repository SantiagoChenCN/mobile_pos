from __future__ import annotations

from typing import Dict

from PySide6.QtCore import QThread, QTimer, Qt, Signal
from PySide6.QtGui import QAction, QCloseEvent, QFont, QIcon
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QComboBox,
    QFileDialog,
    QFrame,
    QFormLayout,
    QGridLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QMainWindow,
    QMenu,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSpinBox,
    QStyle,
    QSystemTrayIcon,
    QVBoxLayout,
    QWidget,
)

from ui.connection_presentation import ConnectionPresentation, present_connection
from ui.controller import UiController
from ui.network import candidate_lan_hosts
from time_display import format_argentina_time


INTERVAL_LABELS: Dict[int, str] = {
    0: "关闭",
    5: "5 分钟",
    15: "15 分钟",
    30: "30 分钟",
    60: "60 分钟",
}


class BackupThread(QThread):
    finished_with_result = Signal(object)
    failed_with_message = Signal(str)

    def __init__(self, controller: UiController):
        super().__init__()
        self.controller = controller

    def run(self) -> None:
        try:
            result = self.controller.run_backup_once()
            self.finished_with_result.emit(result)
        except Exception as exc:
            self.failed_with_message.emit(str(exc))


class MainWindow(QMainWindow):
    def __init__(self, controller: UiController):
        super().__init__()
        self.controller = controller
        self._exiting = False
        self._backup_thread = None
        self._connection_presentation: ConnectionPresentation | None = None
        self._service_start_error = ""
        self._build_ui()
        self._build_tray()
        self._load_config()
        self._connect_events()
        self._start_http_on_launch()
        self._refresh_connection_diagnostic()

        self.refresh_timer = QTimer(self)
        self.refresh_timer.timeout.connect(self.refresh_status)
        self.refresh_timer.start(3000)

        self.backup_timer = QTimer(self)
        self.backup_timer.timeout.connect(self.start_backup)
        self._configure_backup_timer()

        self.refresh_status()
        self.setMinimumSize(860, 680)
        self.resize(960, 760)

    def _build_ui(self) -> None:
        self.setWindowTitle("MobilePosSync 电脑同步工具")
        self.setWindowIcon(self._app_icon())

        root = QWidget()
        root_layout = QVBoxLayout(root)
        root_layout.setContentsMargins(18, 18, 18, 18)
        root_layout.setSpacing(12)

        title = QLabel("电脑同步工具")
        title.setObjectName("title")
        subtitle = QLabel("只读备份鸣盛商品库，并通过局域网提供手机同步下载。")
        subtitle.setObjectName("subtitle")
        root_layout.addWidget(title)
        root_layout.addWidget(subtitle)

        content = QGridLayout()
        content.setSpacing(12)
        content.addWidget(self._status_group(), 0, 0)
        content.addWidget(self._http_group(), 0, 1)
        content.addWidget(self._source_group(), 1, 0)
        content.addWidget(self._backup_group(), 1, 1)
        content.addWidget(self._connection_group(), 2, 0)
        content.addWidget(self._log_group(), 2, 1)
        content.setColumnStretch(0, 1)
        content.setColumnStretch(1, 1)
        root_layout.addLayout(content, 1)

        footer = QHBoxLayout()
        self.save_button = QPushButton("保存设置")
        self.start_stop_button = QPushButton("停止 HTTP 服务")
        self.copy_connection_button = QPushButton("复制全部连接信息")
        footer.addWidget(self.save_button)
        footer.addWidget(self.start_stop_button)
        footer.addStretch(1)
        footer.addWidget(self.copy_connection_button)
        root_layout.addLayout(footer)

        scroll_area = QScrollArea()
        scroll_area.setWidgetResizable(True)
        scroll_area.setFrameShape(QFrame.Shape.NoFrame)
        scroll_area.setWidget(root)
        self.setCentralWidget(scroll_area)
        self.setStyleSheet(
            """
            QWidget {
                font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
                font-size: 14px;
                color: #1f2a2e;
                background: #f6f7f3;
            }
            QLabel#title {
                font-size: 26px;
                font-weight: 700;
                color: #17342f;
            }
            QLabel#subtitle {
                color: #61706a;
            }
            QGroupBox {
                border: 1px solid #d7ded6;
                border-radius: 8px;
                margin-top: 12px;
                padding: 14px 12px 12px 12px;
                background: #ffffff;
                font-weight: 700;
            }
            QGroupBox::title {
                subcontrol-origin: margin;
                left: 12px;
                padding: 0 4px;
            }
            QLineEdit, QComboBox, QSpinBox, QListWidget {
                border: 1px solid #c9d3cc;
                border-radius: 6px;
                padding: 7px;
                background: #fbfcfa;
                font-weight: 400;
            }
            QPushButton {
                border: 1px solid #2d6c61;
                border-radius: 7px;
                padding: 8px 12px;
                background: #2f7d6f;
                color: #ffffff;
                font-weight: 700;
            }
            QPushButton:hover {
                background: #276a5e;
            }
            QPushButton:disabled {
                background: #a8b6b1;
                border-color: #a8b6b1;
            }
            """
        )

    def _status_group(self) -> QGroupBox:
        group = QGroupBox("状态")
        layout = QFormLayout(group)
        self.service_status_label = QLabel("停止")
        self.service_binding_label = QLabel("未绑定")
        self.connection_status_label = QLabel("未生成")
        self.last_backup_label = QLabel("无")
        self.last_request_label = QLabel("无")
        self.last_error_label = QLabel("无")
        layout.addRow("HTTP 服务", self.service_status_label)
        layout.addRow("服务绑定", self.service_binding_label)
        layout.addRow("手机连接", self.connection_status_label)
        layout.addRow("最近备份", self.last_backup_label)
        layout.addRow("最近请求", self.last_request_label)
        layout.addRow("错误", self.last_error_label)
        return group

    def _source_group(self) -> QGroupBox:
        group = QGroupBox("数据库来源")
        layout = QGridLayout(group)
        self.source_mode_combo = QComboBox()
        self.source_mode_combo.addItem("选择具体 .db 文件", "file")
        self.source_mode_combo.addItem("选择文件夹自动寻找", "folder")

        self.db_file_edit = QLineEdit()
        self.db_folder_edit = QLineEdit()
        self.browse_file_button = QPushButton("选择文件")
        self.browse_folder_button = QPushButton("选择文件夹")
        self.detect_button = QPushButton("检测来源")
        self.detect_result_label = QLabel("未检测")
        self.detect_result_label.setWordWrap(True)

        layout.addWidget(QLabel("来源模式"), 0, 0)
        layout.addWidget(self.source_mode_combo, 0, 1, 1, 2)
        layout.addWidget(QLabel("数据库文件"), 1, 0)
        layout.addWidget(self.db_file_edit, 1, 1)
        layout.addWidget(self.browse_file_button, 1, 2)
        layout.addWidget(QLabel("鸣盛文件夹"), 2, 0)
        layout.addWidget(self.db_folder_edit, 2, 1)
        layout.addWidget(self.browse_folder_button, 2, 2)
        layout.addWidget(self.detect_button, 3, 0)
        layout.addWidget(self.detect_result_label, 3, 1, 1, 2)
        layout.setColumnStretch(1, 1)
        return group

    def _backup_group(self) -> QGroupBox:
        group = QGroupBox("备份")
        layout = QFormLayout(group)
        self.interval_combo = QComboBox()
        for minutes, label in INTERVAL_LABELS.items():
            self.interval_combo.addItem(label, minutes)
        self.retention_label = QLabel("最近 5 个")
        self.backup_now_button = QPushButton("立即备份一次")
        layout.addRow("自动备份间隔", self.interval_combo)
        layout.addRow("历史备份保留", self.retention_label)
        layout.addRow("", self.backup_now_button)
        return group

    def _http_group(self) -> QGroupBox:
        group = QGroupBox("HTTP 与连接")
        layout = QGridLayout(group)
        self.port_spin = QSpinBox()
        self.port_spin.setRange(1, 65535)
        self.host_combo = QComboBox()
        self.host_combo.setEditable(True)
        self.token_edit = QLineEdit()
        self.token_edit.setReadOnly(True)
        self.regen_token_button = QPushButton("重新生成")
        self.start_on_boot_check = QCheckBox("开机自动启动")

        layout.addWidget(QLabel("端口"), 0, 0)
        layout.addWidget(self.port_spin, 0, 1, 1, 2)
        layout.addWidget(QLabel("局域网 IP"), 1, 0)
        layout.addWidget(self.host_combo, 1, 1, 1, 2)
        layout.addWidget(QLabel("Token"), 2, 0)
        layout.addWidget(self.token_edit, 2, 1)
        layout.addWidget(self.regen_token_button, 2, 2)
        layout.addWidget(self.start_on_boot_check, 3, 1, 1, 2)
        layout.setColumnStretch(1, 1)
        return group

    def _connection_group(self) -> QGroupBox:
        group = QGroupBox("手机连接信息")
        layout = QGridLayout(group)
        hint = QLabel("手机和电脑必须在同一局域网。手机端手动输入下面三项后测试连接。")
        hint.setWordWrap(True)

        self.connection_host_edit = self._readonly_field()
        self.connection_port_edit = self._readonly_field()
        self.connection_token_edit = self._readonly_field(monospace=True)
        self.connection_http_status_label = QLabel("HTTP 服务：停止")
        self.connection_http_status_label.setWordWrap(True)
        self.connection_health_status_label = QLabel("本机健康检查：未执行")
        self.connection_health_status_label.setWordWrap(True)
        self.connection_binding_card_label = QLabel("未绑定")
        self.connection_warning_label = QLabel()
        self.connection_warning_label.setWordWrap(True)
        self.connection_warning_label.setStyleSheet("color: #a24624; font-weight: 700;")
        self.connection_guidance_label = QLabel()
        self.connection_guidance_label.setWordWrap(True)
        self.copy_host_button = QPushButton("复制IP")
        self.copy_token_button = QPushButton("复制Token")
        self.copy_connection_card_button = QPushButton("复制全部连接信息")

        layout.addWidget(hint, 0, 0, 1, 3)
        layout.addWidget(QLabel("电脑IP"), 1, 0)
        layout.addWidget(self.connection_host_edit, 1, 1)
        layout.addWidget(self.copy_host_button, 1, 2)
        layout.addWidget(QLabel("端口"), 2, 0)
        layout.addWidget(self.connection_port_edit, 2, 1, 1, 2)
        layout.addWidget(QLabel("Token"), 3, 0)
        layout.addWidget(self.connection_token_edit, 3, 1)
        layout.addWidget(self.copy_token_button, 3, 2)
        layout.addWidget(QLabel("实际监听"), 4, 0)
        layout.addWidget(self.connection_binding_card_label, 4, 1, 1, 2)
        layout.addWidget(self.connection_http_status_label, 5, 0, 1, 3)
        layout.addWidget(self.connection_health_status_label, 6, 0, 1, 3)
        layout.addWidget(self.connection_warning_label, 7, 0, 1, 3)
        layout.addWidget(self.connection_guidance_label, 8, 0, 1, 3)
        layout.addWidget(self.copy_connection_card_button, 9, 1, 1, 2)
        layout.setColumnStretch(1, 1)
        return group

    def _readonly_field(self, monospace: bool = False) -> QLineEdit:
        field = QLineEdit()
        field.setReadOnly(True)
        if monospace:
            font = QFont("Consolas")
            font.setStyleHint(QFont.Monospace)
            field.setFont(font)
        return field

    def _log_group(self) -> QGroupBox:
        group = QGroupBox("事件日志")
        layout = QVBoxLayout(group)
        self.log_list = QListWidget()
        layout.addWidget(self.log_list)
        return group

    def _build_tray(self) -> None:
        self.tray = QSystemTrayIcon(self._app_icon(), self)
        menu = QMenu()
        self.tray_open_action = QAction("打开同步工具", self)
        self.tray_backup_action = QAction("立即备份", self)
        self.tray_copy_action = QAction("复制连接信息", self)
        self.tray_exit_action = QAction("退出", self)
        menu.addAction(self.tray_open_action)
        menu.addAction(self.tray_backup_action)
        menu.addAction(self.tray_copy_action)
        menu.addSeparator()
        menu.addAction(self.tray_exit_action)
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(self._on_tray_activated)
        if QSystemTrayIcon.isSystemTrayAvailable():
            self.tray.show()

    def _connect_events(self) -> None:
        self.source_mode_combo.currentIndexChanged.connect(self._update_source_controls)
        self.browse_file_button.clicked.connect(self._browse_file)
        self.browse_folder_button.clicked.connect(self._browse_folder)
        self.detect_button.clicked.connect(self.detect_source)
        self.backup_now_button.clicked.connect(self.start_backup)
        self.save_button.clicked.connect(self.save_settings)
        self.regen_token_button.clicked.connect(self.regenerate_token)
        self.start_stop_button.clicked.connect(self.toggle_http)
        self.copy_connection_button.clicked.connect(self.copy_connection_info)
        self.copy_connection_card_button.clicked.connect(self.copy_connection_info)
        self.copy_host_button.clicked.connect(self.copy_host)
        self.copy_token_button.clicked.connect(self.copy_token)
        self.tray_open_action.triggered.connect(self.show_and_raise)
        self.tray_backup_action.triggered.connect(self.start_backup)
        self.tray_copy_action.triggered.connect(self.copy_connection_info)
        self.tray_exit_action.triggered.connect(self.exit_application)

    def _load_config(self) -> None:
        config = self.controller.config
        self.source_mode_combo.setCurrentIndex(self.source_mode_combo.findData(config.source_mode))
        self.db_file_edit.setText(config.db_file_path)
        self.db_folder_edit.setText(config.db_folder_path)
        self.interval_combo.setCurrentIndex(self.interval_combo.findData(config.backup_interval_minutes))
        self.port_spin.setValue(config.port)
        self.host_combo.clear()
        hosts = candidate_lan_hosts()
        if config.selected_host not in hosts and config.selected_host:
            hosts.append(config.selected_host)
        self.host_combo.addItems(hosts)
        self.host_combo.setCurrentText(config.selected_host)
        self.token_edit.setText(config.token)
        self.start_on_boot_check.setChecked(config.start_on_boot)
        self._update_source_controls()
        self.refresh_connection_info()

    def _start_http_on_launch(self) -> None:
        try:
            self.controller.start_service()
            self._service_start_error = ""
        except Exception as exc:
            self._service_start_error = self._service_start_error_message(exc)
            self.last_error_label.setText(self._service_start_error)
            QMessageBox.warning(self, "HTTP 服务未启动", self._service_start_error)

    def _configure_backup_timer(self) -> None:
        minutes = self.controller.config.backup_interval_minutes
        self.backup_timer.stop()
        if minutes > 0:
            self.backup_timer.setInterval(minutes * 60 * 1000)
            self.backup_timer.start()

    def _update_source_controls(self) -> None:
        mode = self.source_mode_combo.currentData()
        file_mode = mode == "file"
        self.db_file_edit.setEnabled(file_mode)
        self.browse_file_button.setEnabled(file_mode)
        self.db_folder_edit.setEnabled(not file_mode)
        self.browse_folder_button.setEnabled(not file_mode)

    def _browse_file(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "选择鸣盛数据库", "", "SQLite 数据库 (*.db);;所有文件 (*.*)")
        if path:
            self.db_file_edit.setText(path)

    def _browse_folder(self) -> None:
        path = QFileDialog.getExistingDirectory(self, "选择鸣盛软件文件夹")
        if path:
            self.db_folder_edit.setText(path)

    def save_settings(self) -> None:
        try:
            self.controller.save_from_form(
                source_mode=self.source_mode_combo.currentData(),
                db_file_path=self.db_file_edit.text(),
                db_folder_path=self.db_folder_edit.text(),
                backup_interval_minutes=int(self.interval_combo.currentData()),
                port=int(self.port_spin.value()),
                selected_host=self._selected_host_from_combo(),
                start_on_boot=self.start_on_boot_check.isChecked(),
            )
            self._service_start_error = ""
            self._configure_backup_timer()
            self._load_config()
            self._refresh_connection_diagnostic()
            self.refresh_status()
            self.tray.showMessage("MobilePosSync", "设置已保存", QSystemTrayIcon.Information, 2000)
        except Exception as exc:
            service_error = self._service_start_error_message(exc)
            if service_error:
                self._service_start_error = service_error
                self.last_error_label.setText(service_error)
                self._refresh_connection_diagnostic()
                self.refresh_status()
                QMessageBox.warning(self, "HTTP 服务未启动", service_error)
                return
            QMessageBox.warning(self, "保存失败", str(exc))

    def regenerate_token(self) -> None:
        reply = QMessageBox.question(
            self,
            "重新生成 Token",
            "重新生成后，手机端需要重新输入新的 Token。",
        )
        if reply != QMessageBox.Yes:
            return
        self.controller.regenerate_token()
        self._load_config()
        self.refresh_status()

    def detect_source(self) -> None:
        self.save_settings()
        try:
            source = self.controller.detect_source()
            self.detect_result_label.setText(f"找到 CJQ_GOODLIST：{source.path}")
        except Exception as exc:
            self.detect_result_label.setText("检测失败：" + str(exc))

    def start_backup(self) -> None:
        if self._backup_thread is not None and self._backup_thread.isRunning():
            return
        self.backup_now_button.setEnabled(False)
        self.tray_backup_action.setEnabled(False)
        self.last_error_label.setText("无")
        self._backup_thread = BackupThread(self.controller)
        self._backup_thread.finished_with_result.connect(self._backup_finished)
        self._backup_thread.failed_with_message.connect(self._backup_failed)
        self._backup_thread.finished.connect(self._backup_thread.deleteLater)
        self._backup_thread.start()

    def _backup_finished(self, result) -> None:
        self.backup_now_button.setEnabled(True)
        self.tray_backup_action.setEnabled(True)
        if not result.ok:
            self.last_error_label.setText(result.message)
        self.refresh_status()
        self.refresh_connection_info()

    def _backup_failed(self, message: str) -> None:
        self.backup_now_button.setEnabled(True)
        self.tray_backup_action.setEnabled(True)
        self.last_error_label.setText(message)
        self.refresh_status()

    def toggle_http(self) -> None:
        try:
            if self.controller.service_running:
                self.controller.stop_service()
            else:
                self.controller.start_service()
            self._service_start_error = ""
            self._refresh_connection_diagnostic()
            self.refresh_status()
        except Exception as exc:
            self._service_start_error = self._service_start_error_message(exc)
            self.last_error_label.setText(self._service_start_error)
            self._refresh_connection_diagnostic()
            self.refresh_status()
            QMessageBox.warning(self, "HTTP 服务错误", self._service_start_error)

    def copy_connection_info(self) -> None:
        if not self._can_copy_connection():
            return
        QApplication.clipboard().setText(self.controller.connection_summary())
        self.tray.showMessage("MobilePosSync", "连接信息已复制", QSystemTrayIcon.Information, 1800)

    def copy_host(self) -> None:
        if not self._can_copy_connection():
            return
        QApplication.clipboard().setText(self.controller.connection_host())
        self.tray.showMessage("MobilePosSync", "电脑IP已复制", QSystemTrayIcon.Information, 1600)

    def copy_token(self) -> None:
        QApplication.clipboard().setText(self.controller.connection_token())
        self.tray.showMessage("MobilePosSync", "Token已复制", QSystemTrayIcon.Information, 1600)

    def refresh_status(self) -> None:
        if self.controller.service_running:
            self.service_status_label.setText("运行中")
            self.start_stop_button.setText("停止 HTTP 服务")
        else:
            self.service_status_label.setText("停止")
            self.start_stop_button.setText("启动 HTTP 服务")
        self.service_binding_label.setText(self.controller.service_binding_text())
        self._render_connection_presentation()
        self.last_backup_label.setText(self.controller.latest_backup_text())
        self.last_request_label.setText(self.controller.latest_request_text())
        self.refresh_connection_info()
        self._refresh_log()

    def refresh_connection_info(self) -> None:
        self.connection_host_edit.setText(self.controller.connection_host())
        self.connection_port_edit.setText(str(self.controller.connection_port()))
        self.connection_token_edit.setText(self.controller.connection_token())

    def _refresh_connection_diagnostic(self) -> None:
        self._connection_presentation = present_connection(self.controller.network_diagnostics())

    def _render_connection_presentation(self) -> None:
        if self._connection_presentation is None:
            return
        presentation = self._connection_presentation
        self.connection_host_edit.setText(presentation.host)
        self.connection_port_edit.setText(str(presentation.port))
        self.connection_binding_card_label.setText(presentation.bind_address)
        self.connection_http_status_label.setText(presentation.service_text)
        self.connection_health_status_label.setText(presentation.local_health_text)
        self.connection_warning_label.setText(self._service_start_error or presentation.warning_text)
        self.connection_guidance_label.setText(presentation.guidance_text)
        self.connection_status_label.setText(self._connection_status_text(presentation))
        self._set_connection_copy_enabled(presentation.can_copy_connection)

    def _connection_status_text(self, presentation: ConnectionPresentation) -> str:
        if self._service_start_error:
            return self._service_start_error
        if presentation.can_copy_connection:
            return f"可用，手机连接 {presentation.host}:{presentation.port}"
        return presentation.warning_text or presentation.service_text

    def _set_connection_copy_enabled(self, enabled: bool) -> None:
        self.copy_connection_button.setEnabled(enabled)
        self.copy_connection_card_button.setEnabled(enabled)
        self.copy_host_button.setEnabled(enabled)
        self.tray_copy_action.setEnabled(enabled)

    def _selected_host_from_combo(self) -> str:
        return self.host_combo.currentText().strip()

    def _can_copy_connection(self) -> bool:
        return bool(
            self._connection_presentation
            and self._connection_presentation.can_copy_connection
            and not self._service_start_error
        )

    def _service_start_error_message(self, exc: Exception) -> str:
        text = str(exc)
        if "10048" in text or "Address already in use" in text:
            return f"端口 {self.port_spin.value()} 已被占用，请更换端口后再启动 HTTP 服务。"
        if isinstance(exc, OSError):
            return "HTTP 服务启动失败，请检查端口设置后重试。"
        return ""

    def _refresh_log(self) -> None:
        self.log_list.clear()
        for entry in reversed(self.controller.read_events()[-80:]):
            time = format_argentina_time(entry.get("time"))
            level = entry.get("level", "INFO")
            message = entry.get("message", "")
            self.log_list.addItem(f"{time} [{level}] {message}")

    def show_and_raise(self) -> None:
        self.show()
        self.raise_()
        self.activateWindow()

    def exit_application(self) -> None:
        self._exiting = True
        self.backup_timer.stop()
        self.refresh_timer.stop()
        self.controller.stop_service()
        self.tray.hide()
        QApplication.quit()

    def closeEvent(self, event: QCloseEvent) -> None:
        if self._exiting:
            event.accept()
            return
        event.ignore()
        self.hide()
        self.tray.showMessage("MobilePosSync", "工具已最小化到托盘，HTTP 服务继续运行。", QSystemTrayIcon.Information, 2500)

    def _on_tray_activated(self, reason) -> None:
        if reason == QSystemTrayIcon.Trigger:
            self.show_and_raise()

    def _app_icon(self) -> QIcon:
        return self.style().standardIcon(QStyle.StandardPixmap.SP_ComputerIcon)

def launch_gui() -> int:
    from paths import AppPaths

    app = QApplication.instance() or QApplication([])
    app.setQuitOnLastWindowClosed(False)
    controller = UiController(AppPaths.from_environment())
    window = MainWindow(controller)
    window.show()
    return app.exec()
