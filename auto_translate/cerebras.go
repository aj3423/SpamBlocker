package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
)

func parseCerebrasResponse(body []byte) (string, error) {
	var resp ChatCompletion

	if err := json.Unmarshal(body, &resp); err != nil {
		return "", fmt.Errorf("failed to unmarshal response: %w", err)
	}

	if len(resp.Choices) == 0 {
		return "", fmt.Errorf("no choices returned")
	}

	content := resp.Choices[0].Message.Content

	return content, nil
}

func cerebras(model, prompt string) (string, error) {

	url := "https://api.cerebras.ai/v1/chat/completions"

	jsonPayload := fmt.Sprintf(`{
	 "model": "%s",
	 "stream": false,
	 "messages": [{
		 "role": "user",
		 "content": "%s"
	 }],
     "temperature": 0,
     "max_tokens": -1,
     "seed": 0,
     "top_p": 1
    }`, model, strings.Trim(strconv.Quote(prompt), "\""))

	// fmt.Println("payload:")
	// fmt.Println(jsonPayload)

	req, e := http.NewRequest("POST", url, bytes.NewBuffer([]byte(jsonPayload)))
	if e != nil {
		return "", e
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+os.Getenv("CEREBRAS_API_KEY"))

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

	content, err := parseGroqResponse(body)
	if err != nil {
		panic(err)
	}

	return strings.TrimSpace(content), nil

}
