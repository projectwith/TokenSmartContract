pragma solidity ^0.5.0;

import "./IERC20.sol";
import "../../function/DataControl.sol";

/**
 * @title TokenControl
 * @dev Token send and get balance
 */
contract TokenControl is DataControl {
    function setTokenAddr(address _tokenAddr, string memory _tokenName) public onlyAdmin {
        setAddressStorage("WithProject", keccak256(abi.encodePacked(_tokenName)), _tokenAddr);
    }

    function getTokenAddr(string memory _tokenName) public view returns(address) {
        return getAddressStorage("WithProject", keccak256(abi.encodePacked(_tokenName)));
    }

    function sendToken(string memory _tokenName, address _to, uint256 _amt) public onlyAdmin {
        _sendToken(_tokenName, _to, _amt);
    }

    function _sendToken(string memory _tokenName, address _to, uint256 _amt) internal {
        IERC20 token = IERC20(getTokenAddr(_tokenName));
        require(token.transfer(_to,_amt), "RevenueShare : Token Transfer Fail");
    }

    function getBalance(string memory _tokenName) public view returns(uint256){
        IERC20 token = IERC20(getTokenAddr(_tokenName));
        return token.balanceOf(address(this));
    }
}