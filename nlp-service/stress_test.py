"""
stress_test.py
VLASNIK: Član 2

Ova skripta vrši opterećenje (Load/Stress Test) na naš FastAPI NLP mikroservis.
Simulira više paralelnih zahteva (kao da Član 3 procesira stotine presuda istovremeno)
kako bismo proverili propusnu moć (Throughput) i stabilnost spaCy modela.
"""

import concurrent.futures
import requests
import time

# URL našeg lokalnog NLP mikroservisa
URL = "http://localhost:8000/extract"

# Sirov JSON koji šaljemo u svakom zahtevu
PAYLOAD = {
    "text": "Presuda Višeg suda u Beogradu, K-123/2024 od 15.10.2024. Okrivljeni je namerno ispustio 15.5 m3 naftnih derivata u reku. Okrivljeni ranije nije osuđivan, a štetu je sanirao. Sud mu izriče uslovnu osudu.",
    "options": {
        "includeMetadata": True,
        "includeFacts": True,
        "language": "sr"
    }
}
HEADERS = {"Content-Type": "application/json"}

def send_request(req_id):
    """Šalje jedan HTTP POST zahtev našem API-ju."""
    try:
        start_time = time.time()
        # Šaljemo zahtev, postavljamo timeout na 10 sekundi za svaki slučaj
        response = requests.post(URL, json=PAYLOAD, headers=HEADERS, timeout=10)
        end_time = time.time()
        
        return req_id, response.status_code, end_time - start_time
    except requests.exceptions.RequestException as e:
        # Ako API pukne ili ne odgovori na vreme
        return req_id, 500, 0.0

def run_stress_test(total_requests=200, max_workers=20):
    """
    Pokreće thread pool koji konkurentno bombarduje API.
    :param total_requests: Ukupan broj zahteva koji se šalje.
    :param max_workers: Broj 'paralelnih' korisnika u isto vreme.
    """
    print("======================================================")
    print(" POKREĆEM STRESS TEST ZA PRAVNA INFORMATIKA NLP SERVIS")
    print("======================================================")
    print(f" Šaljem ukupno: {total_requests} zahteva")
    print(f" Paralelnih radnika (concurrency): {max_workers}\n")

    start_time = time.time()
    success_count = 0
    error_count = 0
    response_times = []

    # Koristimo ThreadPoolExecutor za paralelno slanje zahteva
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = [executor.submit(send_request, i) for i in range(total_requests)]

        for future in concurrent.futures.as_completed(futures):
            req_id, status, duration = future.result()
            if status == 200:
                success_count += 1
                response_times.append(duration)
            else:
                error_count += 1

    total_time = time.time() - start_time
    avg_time = sum(response_times) / len(response_times) if response_times else 0
    throughput = total_requests / total_time if total_time > 0 else 0

    print("======================================================")
    print(" REZULTATI STRESS TESTA")
    print("======================================================")
    print(f" Ukupno vreme izvršavanja : {total_time:.2f} sekundi")
    print(f" Uspešnih zahteva (200)   : {success_count}")
    print(f" Neuspešnih zahteva       : {error_count}")
    print(f" Prosečno vreme po zahtevu: {avg_time:.4f} sekundi")
    print(f" Propusna moć (Throughput): {throughput:.2f} zahteva/sekundi")
    print("======================================================")
    
    if error_count > 0:
        print("\n[UPOZORENJE] API je počeo da odbija zahteve. Potrebna je optimizacija (gunicorn/uvicorn workers).")
    else:
        print("\n[USPEH] API je stabilan i izdržao je zadato opterećenje bez ijedne greške!")

if __name__ == "__main__":
    run_stress_test(total_requests=200, max_workers=20)