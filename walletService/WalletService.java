package io.withproject.app.api.wallet;

import com.klaytn.caver.Caver;
import com.klaytn.caver.crypto.KlayCredentials;
import com.klaytn.caver.fee.FeePayerManager;
import com.klaytn.caver.methods.response.KlayTransactionReceipt;
import com.klaytn.caver.tx.ValueTransfer;
import com.klaytn.caver.tx.manager.PollingTransactionReceiptProcessor;
import com.klaytn.caver.tx.manager.TransactionManager;
import com.klaytn.caver.tx.manager.TransactionReceiptProcessor;
import com.klaytn.caver.tx.model.KlayRawTransaction;
import com.klaytn.caver.tx.model.SmartContractExecutionTransaction;
import com.klaytn.caver.tx.model.ValueTransferTransaction;
import com.klaytn.caver.utils.ChainId;
import com.klaytn.caver.utils.Convert;
import com.klaytn.caver.wallet.KlayWalletUtils;
import io.withproject.app.api.wallet.constant.LogType;
import io.withproject.app.api.wallet.exception.WalletValidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletLogRepository walletLogRepository;

    @Value([apiUrl])
    private String apiUrl;


    @Value([contractPk])
    private String contractPk;

    @Value([contractAddress])
    private String contractAddress;

    @Value([fee])
    private String feePk;

    @Value([walletPath])
    private String walletPath;

    @Value([chainid])
    private int chainid;


    public Wallet makeWallet(Long memberId) {
        Wallet wallet = new Wallet();
        try {
            String password = RandomStringUtils.randomNumeric(6);
            String fileName = KlayWalletUtils.generateNewWalletFile("", password, new File(walletPath)); // caver-java
            Credentials credentials = WalletUtils.loadCredentials(
                    password,
                    fileName);
//            String privateKeyGenerated = credentials.getEcKeyPair().getPrivateKey().toString(16);
            String privateKeyGenerated = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
            String publicKey = credentials.getEcKeyPair().getPublicKey().toString(16);
            String addr = credentials.getAddress();
            wallet.setMemberId(memberId);
            wallet.setFileName(fileName);
            wallet.setPassword(password);
            wallet.setPublicKey(publicKey);
            wallet.setAddr(addr);
            wallet.setPrivateKey(privateKeyGenerated);

        } catch (CipherException e) {
            e.printStackTrace();
            throw new WalletValidException();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            throw new WalletValidException();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new WalletValidException();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            throw new WalletValidException();
        } catch (IOException e) {
            e.printStackTrace();
            throw new WalletValidException();
        }

        return wallet;
    }

    public Integer post(Wallet wallet) {
        walletRepository.save(wallet);
        return 1;
    }

    public WalletLog postLog(Long memberId, LogType logType) {
        WalletLog walletLog = new WalletLog();
        walletLog.setMemberId(memberId);
        walletLog.setLogType(logType);
        walletLog.setToAddress(contractAddress);
        walletLogRepository.save(walletLog);
        callLogContract(walletLog);
        return walletLog;
    }


    public void callLogContract(WalletLog walletLog) {
        try {
            Caver caver = Caver.build(apiUrl);
            KlayCredentials credentials = KlayCredentials.create(contractPk);
            Function function = new Function(
                    "pushHistory",  // FUNC_TRANSFER = "transfer"
                    Arrays.asList(new Uint256(walletLog.getId()), new Utf8String(walletLog.getMemberId().toString()), new Address(walletLog.getToAddress()), new Uint256(1), new Utf8String(walletLog.getLogType().shout()), new Utf8String(walletLog.getLogType().getCode())),  // inputParameters
                    Collections.emptyList()  // outputParameters
            );
            String data = FunctionEncoder.encode(function);

            TransactionManager transactionManager = new TransactionManager.Builder(caver, credentials)
                    .setChaindId(chainid).build();  // BAOBAB_TESTNET = 1001
            SmartContractExecutionTransaction smartContractExecution =
                    SmartContractExecutionTransaction.create(
                            credentials.getAddress(),  // fromAddress
                            contractAddress,  // contractAddress
                            BigInteger.ZERO,  // value
                            Numeric.hexStringToByteArray(data),  // data
                            BigInteger.valueOf(300_000)  // gasLimit
                    );
            String senderRawTransaction = transactionManager.sign(smartContractExecution, true).getValueAsString();
            log.debug("]-----] senderRawTransaction [-----[ {}", senderRawTransaction);
            KlayCredentials feePayer = KlayCredentials.create(feePk);
            FeePayerManager feePayerManager = new FeePayerManager.Builder(caver, feePayer).setChainId(chainid).build();
            feePayerManager.executeTransaction(senderRawTransaction);
        } catch (Exception e) {
            log.debug("]-----] error [-----[ {}", e);
        }
    }


    public void sendToken(String toAddress, BigDecimal sendValue) {

        try {
            BigInteger sendValueWiken = Convert.toPeb(sendValue, Convert.Unit.KLAY).toBigInteger(); // caver-java
            Caver caver = Caver.build(apiUrl);
            KlayCredentials credentials = KlayCredentials.create(contractPk);
            Function function = new Function(
                    "sendToken",  // FUNC_TRANSFER = "transfer"
                    Arrays.asList(new Utf8String("WIKEN"), new Address(toAddress), new Uint256(sendValueWiken)),  // inputParameters
                    Collections.emptyList()  // outputParameters
            );
            String data = FunctionEncoder.encode(function);

            TransactionManager transactionManager = new TransactionManager.Builder(caver, credentials)
                    .setChaindId(chainid).build();  // BAOBAB_TESTNET = 1001
            SmartContractExecutionTransaction smartContractExecution =
                    SmartContractExecutionTransaction.create(
                            credentials.getAddress(),  // fromAddress
                            contractAddress,  // contractAddress
                            BigInteger.ZERO,  // value
                            Numeric.hexStringToByteArray(data),  // data
                            BigInteger.valueOf(100_000)  // gasLimit
                    );
            String senderRawTransaction = transactionManager.sign(smartContractExecution, true).getValueAsString();
            log.debug("]-----] senderRawTransaction [-----[ {}", senderRawTransaction);

            KlayCredentials feePayer = KlayCredentials.create(feePk);
            FeePayerManager feePayerManager = new FeePayerManager.Builder(caver, feePayer).setChainId(chainid).build();
            feePayerManager.executeTransaction(senderRawTransaction);
        } catch (Exception e) {

        }

    }

}
