package com.yiwalo.tunnel.config;

public enum TypeConnexion {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    SLOWDNS("SlowDNS"),
    SSH("SSH"),
    HYSTERIA2("Hysteria2"),
    WIREGUARD("WireGuard"),
    SOCKS("SOCKS"),
    TUIC("TUIC");

    public final String libelle;

    TypeConnexion(String libelle) {
        this.libelle = libelle;
    }
}
