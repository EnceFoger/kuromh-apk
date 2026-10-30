#!/system/bin/sh
# KUROMH Controller - penjaga uninstall.
# Dipasang oleh aplikasi KUROMH saat install modul (dijalankan otomatis oleh Magisk/KernelSU saat boot).
# Jika aplikasi KUROMH dihapus, modul ini dinetralkan lalu dihapus otomatis.
MODDIR=${0%/*}
APP=com.kuromh.controller
INTERVAL=20      # detik antar pengecekan
NEED_MISSES=3    # berapa kali berturut-turut aplikasi harus hilang sebelum modul dihapus

# Kembalikan config ke nilai netral supaya game yang sedang berjalan langsung normal
neutralize_config() {
    pkg=$(grep -v '^#' "$MODDIR/package.txt" 2>/dev/null | head -n 1 | tr -d ' \r')
    [ -z "$pkg" ] && pkg=$(grep -v '^#' "$MODDIR/package.txt.off" 2>/dev/null | head -n 1 | tr -d ' \r')
    [ -z "$pkg" ] && pkg=com.bandainamcoent.bleach_mh_ww
    for f in \
        "/storage/emulated/0/Android/data/$pkg/files/kuro_mirror_high.cfg" \
        "/data/media/0/Android/data/$pkg/files/kuro_mirror_high.cfg" \
        "/data/data/$pkg/files/kuro_mirror_high.cfg"; do
        [ -f "$f" ] || continue
        sed -i \
            -e 's/^unity_speed=[0-9.]*/unity_speed=1.0/' \
            -e 's/^damage_rate=[0-9.]*/damage_rate=1.0/' \
            -e 's/^god_mode=[0-9a-z]*/god_mode=0/' \
            "$f"
    done
}

(
    while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 5; done
    sleep 30
    misses=0
    while [ -d "$MODDIR" ]; do
        list=$(pm list packages 2>/dev/null)
        # Hitung hanya jika package manager benar-benar menjawab (daftar berisi package:android)
        if echo "$list" | grep -qx 'package:android'; then
            if echo "$list" | grep -qx "package:$APP"; then
                misses=0
            else
                misses=$((misses + 1))
            fi
            if [ "$misses" -ge "$NEED_MISSES" ]; then
                neutralize_config
                touch "$MODDIR/disable" "$MODDIR/remove"
                rm -rf "$MODDIR"
                break
            fi
        fi
        sleep "$INTERVAL"
    done
) &
