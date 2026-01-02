package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
)

type GenerateResponse struct {
	Response string `json:"response"` // The generated text
}

func ollama(model, prompt string) (string, error) {
	url := "http://localhost:11434/api/generate"

	jsonPayload := fmt.Sprintf(`{
        "model": "%s",
        "prompt": "%s",
		"stream": false
    }`, model, strings.Trim(strconv.Quote(prompt), "\""))

	// fmt.Println("payload:")
	// fmt.Println(jsonPayload)

	req, e := http.NewRequest("POST", url, bytes.NewBuffer([]byte(jsonPayload)))
	if e != nil {
		return "", e
	}

	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{}
	resp, e := client.Do(req)
	if e != nil {
		return "", e
	}
	defer resp.Body.Close()
	body, e := io.ReadAll(resp.Body)
	if e != nil {
		return "", e
	}
	// fmt.Println("body: ")
	// fmt.Println(string(body))

	var result GenerateResponse
	if e := json.NewDecoder(bytes.NewReader(body)).Decode(&result); e != nil {
		return "", e
	}
	return result.Response, nil

}
