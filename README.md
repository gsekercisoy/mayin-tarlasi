# Mayın Tarlası

React (Vite) arayüz + Java (Spring Boot 2.7) REST API + H2 veritabanı.
**Mayın haritası tamamen sunucuda** üretilir ve saklanır — istemci oyun bitene kadar mayın konumlarını göremez
(IRT yanıtı UTEX'te "mine" alanı yalnızca `status != ACTIVE` iken döner).

## Özellikler

- 3 zorluk: EASY 9×9/10 💣, MEDIUM 12×12/25, HARD 16×16/45
- **İlk tıklama her zaman güvenli**: mayınlar ilk açılışta yerleştirilir (tıklanan + komşuları hariç) → ilk ışıkta büyük flood-fill açılımı
- Sunucu tarafı **flood-fill**: 0 komşulu hücreler BFS ile toplu açılır
- Sağ tık = **bayrak koyma/kaldırma** (bayrak sayısı mayın sayısıyla sınırlı; açık hücreye bayrak → 400)
- Win/kayıp süre damgası + **rekor tablosu** (zorluk başına en hızlı 10 kazanma, `leaderboard`)
- Oyun bittiğinde tüm mayınlar açığa çıkar (`acigaCikanMayin` doğrulandı)
- Doğrulama: geçersiz zorluk/sınır dışı hücre/zaten açık hücre/isaretli hücre açma → 400, bitmiş oyuna dokunma → 409

## Çalıştırma

```bash
cd backend && mvn spring-boot:run     # API :8080
cd frontend && npm install && npm run dev   # UI :5173 (proxy → 8080)
```

## API

| Metot | Yol | Açıklama |
|---|---|---|
| POST | `/api/games` | `{player, difficulty}` → 201 tahta görünümü (mayınlar gizli) |
| GET | `/api/games/{id}` | Mevcut oyun durumu |
| POST | `/api/games/{id}/reveal` | `{row, col}` — hücreyi aç (flood-fill, mayın→LOST, tamamlama→WON) |
| POST | `/api/games/{id}/flag` | `{row, col}` — bayrak toggle |
| GET | `/api/leaderboard?difficulty=` | En hızlı 10 kazanan (tüm zorluklar için parametresiz) |
| GET | `/api/stats` | `{totalGames, active, won, lost, fastestWinSec}` |

Duman testi: gizlilik ✓ (ACTIVE durumunda yanıt `"mine"` alanı içermiyor), ilk tıklama 57 hücre flood ✓,
flag toggle + korunumu ✓, LOST sonrası 10 mayın açık ✓, negatif akışlar 400/409 ✓
