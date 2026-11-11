#!/usr/bin/env python3
"""Apply the Pingora paired envelope adapter to the pinned private build tree."""
from pathlib import Path
import sys


def apply(source: Path):
    def patch(name, before, after):
        p = source / name
        s = p.read_text()
        if after in s:
            return
        if before not in s:
            raise RuntimeError(f'Pinned bridge context missing: {name}')
        p.write_text(s.replace(before, after, 1))

    patch('core/device.h', '#include <QString>', '#include <QString>\n#include <QStringList>')
    patch('core/device.h', '    Q_SCRIPTABLE void requestPairing();', '''    // Local bus adapter; the existing service owns authenticated TLS sessions.
    Q_SCRIPTABLE bool sendEnvelope(const QString &json);
    Q_SCRIPTABLE QStringList takeEnvelopes();
    Q_SCRIPTABLE void requestPairing();''')
    patch('core/device.cpp', '#include <QSet>', '#include <QSet>\n#include <QJsonDocument>\n#include <QJsonObject>')
    patch('core/device.cpp', '    DeviceInfo m_deviceInfo;', '    QStringList m_pingoraEnvelopes;\n    DeviceInfo m_deviceInfo;')
    patch('core/device.cpp', 'void Device::privateReceivedPacket(const NetworkPacket &np)', '''bool Device::sendEnvelope(const QString &json)
{
    if (!isPaired() || !isReachable() || json.toUtf8().size() > 32768) return false;
    auto document = QJsonDocument::fromJson(json.toUtf8());
    if (!document.isObject()) return false;
    NetworkPacket packet(QStringLiteral("pingora.envelope"));
    packet.set(QStringLiteral("json"), json);
    return sendPacket(packet);
}

QStringList Device::takeEnvelopes()
{
    if (!isPaired() || !isReachable()) { d->m_pingoraEnvelopes.clear(); return {}; }
    QStringList batch;
    while (!d->m_pingoraEnvelopes.isEmpty() && batch.size() < 8)
        batch.append(d->m_pingoraEnvelopes.takeFirst());
    return batch;
}

void Device::privateReceivedPacket(const NetworkPacket &np)''')
    patch('core/device.cpp', '    } else if (isPaired()) {\n        const QList<KdeConnectPlugin *> plugins', '''    } else if (isPaired()) {
        if (np.type() == QStringLiteral("pingora.envelope")) {
            if (np.payload()) { np.payload()->close(); return; }
            const QString json = np.get<QString>(QStringLiteral("json"));
            if (json.toUtf8().size() > 32768 || d->m_pingoraEnvelopes.size() >= 128) return;
            if (!QJsonDocument::fromJson(json.toUtf8()).isObject()) return;
            d->m_pingoraEnvelopes.append(json);
            return;
        }
        const QList<KdeConnectPlugin *> plugins''')
    patch('core/device.cpp', '    KdeConnectConfig::instance().removeTrustedDevice(id());', '    d->m_pingoraEnvelopes.clear();\n    KdeConnectConfig::instance().removeTrustedDevice(id());')
    patch('core/device.cpp', '    if (d->m_deviceLinks.isEmpty()) {\n        reloadPlugins();', '    if (d->m_deviceLinks.isEmpty()) {\n        d->m_pingoraEnvelopes.clear();\n        reloadPlugins();')
    # No dynamic plugin discovery: advertise only the paired adapter capability.
    p = source / 'core/pluginloader.cpp'
    s = p.read_text().replace('    QSet<QString> ret;\n    for (const KPluginMetaData &service : plugins)', '    QSet<QString> ret{QStringLiteral("pingora.envelope")};\n    for (const KPluginMetaData &service : plugins)')
    p.write_text(s)


if __name__ == '__main__':
    apply(Path(sys.argv[1]))
