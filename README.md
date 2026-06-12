# Paralel Point-in-Polygon Tespiti

Paralel Programlama Dersi — Grup 4 (+10 Bonus Puan)

## Problem

Konkav veya konveks bir poligon için verilen nokta kümesinin hangi noktalarının poligonun içinde olduğunu paralel programlama ile tespit etmek.

## Algoritma

**Ray Casting (Işın İzleme):** Test noktasından yatay bir ışın gönderilerek poligon kenarlarıyla kesişim sayısı hesaplanır. Tek sayı → içeride, çift sayı → dışarıda.

- Zaman karmaşıklığı: O(n)
- Hem konveks hem konkav poligonları destekler

## Paralel Çözüm

Java `ExecutorService` ile thread havuzu kullanılarak M nokta, T thread'e eşit parçalara bölünür. Thread'ler arasında veri bağımlılığı yoktur.

## Dosyalar

| Dosya | Açıklama |
|---|---|
| `src/PointInPolygon.java` | Ana algoritma + benchmark (sıralı & paralel) |
| `src/PolygonVisualizer.java` | Animasyonlu Swing görselleştirici |
| `compile_and_run.bat` | Derleme ve çalıştırma betiği |
| `Rapor.docx` | Proje raporu |
| `VIDEO_LINK.txt` | Sunum videosu linki |

## Çalıştırma

```bash
# Derle ve çalıştır
compile_and_run.bat

# Sadece benchmark
javac --release 8 -d bin src/PointInPolygon.java
java -cp bin PointInPolygon

# Görselleştirici
javac --release 8 -d bin src/PolygonVisualizer.java
java -cp bin PolygonVisualizer
```

## Hızlanma Sonuçları

| Nokta Sayısı | Thread | Sıralı (ms) | Paralel (ms) | Hızlanma |
|:---:|:---:|:---:|:---:|:---:|
| 100.000 | 4 | 3 | 1 | 3.00× |
| 500.000 | 4 | 14 | 4 | 3.50× |
| 1.000.000 | 8 | 24 | 6 | 4.00× |
| 5.000.000 | 8 | 101 | 30 | 3.37× |

## Video

[Google Drive — Sunum Videosu](https://drive.google.com/file/d/1pnuTolDeu92ZCpKlFoyhwuiBukSR1Ox9/view?usp=sharing)
