package main

import (
	"fmt"
	"io"
	"os"

	"filippo.io/age"
)

func fail(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "vitalis-age: "+format+"\n", args...)
	os.Exit(1)
}

func main() {
	if len(os.Args) != 4 {
		fail("usage: vitalis-age encrypt|decrypt INPUT OUTPUT")
	}
	passphrase := os.Getenv("VITALIS_SIGNING_BACKUP_PASSPHRASE")
	if passphrase == "" {
		fail("VITALIS_SIGNING_BACKUP_PASSPHRASE is required")
	}

	in, err := os.Open(os.Args[2])
	if err != nil {
		fail("open input: %v", err)
	}
	defer in.Close()

	out, err := os.OpenFile(os.Args[3], os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		fail("create output: %v", err)
	}
	ok := false
	defer func() {
		out.Close()
		if !ok {
			os.Remove(os.Args[3])
		}
	}()

	switch os.Args[1] {
	case "encrypt":
		recipient, err := age.NewScryptRecipient(passphrase)
		if err != nil {
			fail("create scrypt recipient: %v", err)
		}
		writer, err := age.Encrypt(out, recipient)
		if err != nil {
			fail("start encryption: %v", err)
		}
		if _, err := io.Copy(writer, in); err != nil {
			fail("encrypt: %v", err)
		}
		if err := writer.Close(); err != nil {
			fail("finish encryption: %v", err)
		}
	case "decrypt":
		identity, err := age.NewScryptIdentity(passphrase)
		if err != nil {
			fail("create scrypt identity: %v", err)
		}
		reader, err := age.Decrypt(in, identity)
		if err != nil {
			fail("start decryption: %v", err)
		}
		if _, err := io.Copy(out, reader); err != nil {
			fail("decrypt: %v", err)
		}
	default:
		fail("unknown operation %q", os.Args[1])
	}

	if err := out.Close(); err != nil {
		fail("close output: %v", err)
	}
	ok = true
}
