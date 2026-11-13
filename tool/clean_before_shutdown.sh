#!/bin/bash
# clean_before_shutdown.sh
# Created for Linux Mint Cinnamon - Java/Firefox stres sonrası güvenli kapatma
# Kullanım: ./clean_before_shutdown.sh

echo ""
echo "🔄 SİSTEM TEMİZLİK VE GÜVENLİ KAPATMA ARACI"
echo "==========================================="

# Renkli output için fonksiyonlar
print_green() {
    echo -e "\e[32m✅ $1\e[0m"
}

print_blue() {
    echo -e "\e[34m🔧 $1\e[0m"
}

print_red() {
    echo -e "\e[31m❌ $1\e[0m"
}

print_yellow() {
    echo -e "\e[33m⚠️  $1\e[0m"
}

# Root kontrolü
check_root() {
    if [ "$EUID" -eq 0 ]; then
        print_red "Bu script root olarak çalıştırılmamalı!"
        exit 1
    fi
}

# Sistem bilgilerini göster
show_system_info() {
    echo ""
    print_blue "Sistem Durumu:"
    echo "CPU Kullanımı: $(grep 'cpu ' /proc/stat | awk '{print ($2+$4)*100/($2+$4+$5)}' | cut -c1-4)%"
    echo "Memory: $(free -h | grep Mem | awk '{print $3"/"$2}')"
    echo "Java Prosesleri: $(pgrep -c java)"
    echo "Firefox Prosesleri: $(pgrep -c firefox)"
    echo ""
}

# Proses temizliği
clean_processes() {
    print_blue "Proses Temizliği Başlıyor..."
    
    local java_count=$(pgrep -c java)
    local firefox_count=$(pgrep -c firefox)
    
    if [ $java_count -gt 0 ]; then
        print_yellow "Java prosesleri sonlandırılıyor: $java_count adet"
        pkill -f java
        sleep 2
        # Zorla sonlandırma gerekirse
        if [ $(pgrep -c java) -gt 0 ]; then
            print_yellow "Zorla sonlandırma: java"
            pkill -9 -f java
        fi
    else
        print_green "Java prosesi yok"
    fi
    
    if [ $firefox_count -gt 0 ]; then
        print_yellow "Firefox sonlandırılıyor: $firefox_count adet"
        pkill firefox
        sleep 2
        # Zorla sonlandırma gerekirse
        if [ $(pgrep -c firefox) -gt 0 ]; then
            print_yellow "Zorla sonlandırma: firefox"
            pkill -9 firefox
        fi
    else
        print_green "Firefox prosesi yok"
    fi
    
    # Diğer potansiyel stresli uygulamalar
    print_blue "Diğer uygulamalar kontrol ediliyor..."
    pkill -f javac 2>/dev/null
}

# Disk sync ve cache temizliği
clean_system_cache() {
    print_blue "Disk ve Cache Temizliği..."
    
    # Disk yazmalarını tamamla
    print_yellow "Disk sync ediliyor..."
    sync
    sudo sync 2>/dev/null
    
    # Kernel cache temizleme
    if [ -w /proc/sys/vm/drop_caches ]; then
        print_yellow "Kernel cache temizleniyor..."
        echo 3 | sudo tee /proc/sys/vm/drop_caches > /dev/null 2>&1
    else
        print_yellow "Kernel cache temizliği için sudo gerekli"
        echo 3 | sudo tee /proc/sys/vm/drop_caches > /dev/null
    fi
    
    # SLAB cache temizleme (mümkünse)
    if command -v slabtop >/dev/null 2>&1; then
        print_yellow "SLAB cache kontrolü..."
        echo "SLAB kullanımı: $(cat /proc/meminfo | grep Slab | awk '{print $2}') kB"
    fi
}

# Network bağlantılarını temizle
clean_network() {
    print_blue "Network Bağlantıları Temizleniyor..."
    
    # Firefox network connections (force close)
    if pgrep firefox > /dev/null; then
        print_yellow "Firefox network bağlantıları kapatılıyor"
        pkill -9 firefox
    fi
}

# Sistem yükünü kontrol et
check_system_load() {
    echo ""
    print_blue "Sistem Yükü Kontrolü:"
    local load=$(cat /proc/loadavg | awk '{print $1}')
    local load_percent=$(echo "scale=2; $load * 100 / $(nproc)" | bc -l 2>/dev/null || echo "N/A")
    
    echo "System Load: $load ($load_percent%)"
    echo "Çalışan prosesler: $(ps aux | wc -l)"
    
    if (( $(echo "$load > 2.0" | bc -l 2>/dev/null || echo 0) )); then
        print_yellow "Sistem yükü hala yüksek, ek bekleme gerekebilir"
        return 1
    else
        print_green "Sistem yükü normal"
        return 0
    fi
}

# Ana temizlik fonksiyonu
main_cleanup() {
    check_root
    show_system_info
    
    print_yellow "Temizlik başlatılsın mı? (e/h)"
    read -r response
    case "$response" in
        [eE][vV][eE][tT]|[eE]) 
            echo ""
            ;;
        *)
            print_red "İptal edildi"
            exit 0
            ;;
    esac
    
    # Temizlik adımları
    clean_processes
    clean_network
    clean_system_cache
    
    # Son durum kontrolü
    show_system_info
    check_system_load
    
    echo ""
    print_green "=== TEMİZLİK TAMAMLANDI ==="
    echo ""
    
    # Geri sayım
    print_yellow "🕒 30 saniye bekleniyor (sistemin stabilize olması için)"
    print_yellow "   Bu süre sonunda kapat butonuna GÜVENLE basabilirsiniz"
    echo ""
    
    for i in {30..1}; do
        echo -ne "\r⏳ Kalan: ${i}s | Çıkış için Ctrl+C     "
        sleep 1
    done
    
    echo ""
    print_green "🎉 ŞİMDİ GÜVENLE KAPAT BUTONUNA BASABİLİRSİNİZ!"
    echo ""
}

# Script başlatma
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main_cleanup
fi